"""Host SQLite checks for the SQL used by LedgerDb's v1/v2/v3 -> v4 upgrade.

Run with Python 3. Android SQLiteOpenHelper behavior is covered separately by
DeviceChecks.investmentMigrationChecks on a disposable emulator.
"""
from pathlib import Path
import re
import sqlite3
import unittest


SOURCE = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/kr/chagok/ledger/LedgerDb.java").read_text(encoding="utf-8")
SCHEMA = re.search(r'"CREATE TABLE "\+table\+" (.*?)"\);', SOURCE).group(1)
UPGRADE = SOURCE.split("if(from<4) {", 1)[1].split("}", 1)[0]
FEATURE_SQL = re.search(r'createPlanning\(SQLiteDatabase db\)\{db.execSQL\("(.*?)"\);', SOURCE).group(1)
STATEMENTS = re.findall(r'db.execSQL\("(.*?)"\);', UPGRADE)
V1_SCHEMA = """CREATE TABLE entries (
    id TEXT PRIMARY KEY NOT NULL, date TEXT NOT NULL,
    type TEXT NOT NULL CHECK(type IN ('수입','지출','저축')),
    amount INTEGER NOT NULL CHECK(amount>0 AND amount<=999999999999),
    category TEXT NOT NULL, memo TEXT NOT NULL)"""
ROWS = [(3, "income", "2026-10-01", "수입", 3000, "월급", "급여"),
        (17, "saving", "2026-10-01", "저축", 700, "주식", "기존 구분 유지"),
        (21, "expense", "2026-10-02", "지출", 500, "식비", "점심")]


class MigrationChecks(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute(V1_SCHEMA)
        self.db.execute("CREATE INDEX entries_date ON entries(date)")
        self.db.execute("PRAGMA user_version=1")
        self.db.executemany("INSERT INTO entries (rowid,id,date,type,amount,category,memo) VALUES (?,?,?,?,?,?,?)", ROWS)
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def migrate(self, fail_after_drop=False, from_version=1):
        with self.db:
            self.db.execute("BEGIN")
            self.db.execute("CREATE TABLE entries_next " + SCHEMA)
            for sql in STATEMENTS:
                self.db.execute(sql)
                if fail_after_drop and sql == "DROP TABLE entries":
                    raise RuntimeError("Simulated upgrade interruption")
            if from_version < 3:
                self.db.execute(FEATURE_SQL)
            self.db.execute("PRAGMA user_version=4")

    def make_v3(self):
        """Exact 0.7.1 (DB3) shape: four types only, plus feature_state with planning and repeat data."""
        v3_schema = SCHEMA.replace(",'저축회수'", "")
        self.assertNotEqual(v3_schema, SCHEMA)
        self.db.execute("DROP TABLE entries")
        self.db.execute("CREATE TABLE entries " + v3_schema)
        self.db.execute("CREATE INDEX entries_date ON entries(date)")
        self.db.executemany("INSERT INTO entries (rowid,id,date,type,amount,category,memo) VALUES (?,?,?,?,?,?,?)", ROWS)
        self.db.execute("INSERT INTO entries (rowid,id,date,type,amount,category,memo) VALUES (50,'invest','2026-11-01','투자',500,'ETF','매수')")
        self.db.execute(FEATURE_SQL)
        self.db.execute("INSERT INTO feature_state VALUES ('planning','keep-me')")
        self.db.execute("PRAGMA user_version=3")
        self.db.commit()

    def test_preserves_every_column_id_and_rowid(self):
        self.migrate()
        self.assertEqual(ROWS, self.db.execute("SELECT rowid,* FROM entries ORDER BY rowid").fetchall())
        self.assertEqual(4, self.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertIn("entries_date", [r[1] for r in self.db.execute("PRAGMA index_list(entries)")])
        self.assertEqual(["expense", "saving", "income"], [r[0] for r in self.db.execute("SELECT id FROM entries ORDER BY date DESC,rowid DESC")])

    def test_allows_investment_preserves_constraints(self):
        self.migrate()
        self.db.execute("INSERT INTO entries VALUES ('investment','2026-10-03','투자',400,'ETF','매수')")
        self.assertEqual(400, self.db.execute("SELECT amount FROM entries WHERE type='투자'").fetchone()[0])
        for kind, amount in [("이체", 1), ("투자", 0), ("투자", -1), ("투자", 1000000000000)]:
            with self.subTest(kind=kind, amount=amount), self.assertRaises(sqlite3.IntegrityError):
                self.db.execute("INSERT INTO entries VALUES ('bad','2026-10-03',?,?,'','')", (kind, amount))

    def test_empty_database(self):
        self.db.execute("DELETE FROM entries")
        self.db.commit()
        self.migrate()
        self.assertEqual(0, self.db.execute("SELECT count(*) FROM entries").fetchone()[0])

    def test_v2_upgrade_preserves_investments_and_rowids(self):
        self.db.execute("DROP TABLE entries")
        self.db.execute("CREATE TABLE entries " + SCHEMA)
        self.db.execute("CREATE INDEX entries_date ON entries(date)")
        self.db.executemany("INSERT INTO entries (rowid,id,date,type,amount,category,memo) VALUES (?,?,?,?,?,?,?)", ROWS)
        self.db.execute("INSERT INTO entries (rowid,id,date,type,amount,category,memo) VALUES (50,'invest','2026-11-01','투자',500,'ETF','매수')")
        self.db.execute("PRAGMA user_version=2")
        self.db.commit()
        before=self.db.execute("SELECT rowid,* FROM entries ORDER BY rowid").fetchall()
        with self.db:
            self.db.execute(FEATURE_SQL)
            self.db.execute("PRAGMA user_version=3")
        self.assertEqual(before,self.db.execute("SELECT rowid,* FROM entries ORDER BY rowid").fetchall())
        self.db.execute("INSERT INTO feature_state VALUES ('planning','test')")
        self.assertEqual('test',self.db.execute("SELECT value FROM feature_state").fetchone()[0])

    def test_v3_upgrade_keeps_rows_rowids_planning_and_allows_release(self):
        self.make_v3()
        before = self.db.execute("SELECT rowid,* FROM entries ORDER BY rowid").fetchall()
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO entries VALUES ('r','2026-11-15','저축회수',1800000,'적금','')")
        self.db.rollback()
        self.migrate(from_version=3)
        self.assertEqual(before, self.db.execute("SELECT rowid,* FROM entries ORDER BY rowid").fetchall())
        self.assertEqual([("planning", "keep-me")], self.db.execute("SELECT * FROM feature_state").fetchall())
        self.assertEqual(4, self.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertIn("entries_date", [r[1] for r in self.db.execute("PRAGMA index_list(entries)")])
        self.db.execute("INSERT INTO entries VALUES ('move:x:saving','2026-11-15','저축회수',1800000,'적금','해지')")
        self.assertEqual(1800000, self.db.execute("SELECT amount FROM entries WHERE type='저축회수'").fetchone()[0])
        for kind, amount in [("이체", 1), ("저축회수", 0), ("저축회수", -1), ("저축회수", 1000000000000)]:
            with self.subTest(kind=kind, amount=amount), self.assertRaises(sqlite3.IntegrityError):
                self.db.execute("INSERT INTO entries VALUES ('bad','2026-10-03',?,?,'','')", (kind, amount))

    def test_failed_v3_upgrade_rolls_back(self):
        self.make_v3()
        before = self.db.execute("SELECT rowid,* FROM entries ORDER BY rowid").fetchall()
        with self.assertRaises(RuntimeError):
            self.migrate(fail_after_drop=True, from_version=3)
        self.assertEqual(before, self.db.execute("SELECT rowid,* FROM entries ORDER BY rowid").fetchall())
        self.assertEqual(3, self.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertEqual([("planning", "keep-me")], self.db.execute("SELECT * FROM feature_state").fetchall())
        self.assertEqual([], self.db.execute("SELECT name FROM sqlite_master WHERE name='entries_next'").fetchall())
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO entries VALUES ('r','2026-11-15','저축회수',1,'적금','')")

    def test_failed_upgrade_rolls_back(self):
        with self.assertRaises(RuntimeError):
            self.migrate(fail_after_drop=True)
        self.assertEqual(ROWS, self.db.execute("SELECT rowid,* FROM entries ORDER BY rowid").fetchall())
        self.assertEqual(1, self.db.execute("PRAGMA user_version").fetchone()[0])
        self.assertEqual([], self.db.execute("SELECT name FROM sqlite_master WHERE name='entries_next'").fetchall())
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO entries VALUES ('investment','2026-10-03','투자',400,'ETF','매수')")


if __name__ == "__main__":
    unittest.main(verbosity=2)
