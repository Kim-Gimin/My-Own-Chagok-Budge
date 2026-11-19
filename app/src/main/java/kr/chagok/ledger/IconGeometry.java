package kr.chagok.ledger;

/** 24×24 아이콘 좌표계를 화면 칸에 맞추는 계산과 톱니바퀴 윤곽. Android 의존성 없음. */
final class IconGeometry {
    static final float GRID = 24f;
    static final float GEAR_OUTER = 9.6f, GEAR_ROOT = 7.2f, GEAR_HOLE = 3f;
    static final int GEAR_TEETH = 8;

    private IconGeometry() {}

    /**
     * 패딩을 뺀 영역 안에서 가로·세로 같은 비율로 24 격자를 가운데 배치한다.
     * @return {scale, offsetX, offsetY}
     */
    static float[] fit(int width, int height, int left, int top, int right, int bottom) {
        float w = Math.max(0, width - left - right), h = Math.max(0, height - top - bottom);
        float side = Math.min(w, h), scale = side / GRID;
        return new float[]{scale, left + (w - side) / 2f, top + (h - side) / 2f};
    }

    /** 톱니 8개의 닫힌 윤곽선 꼭짓점 {x0,y0,x1,y1,...}. 위쪽 정가운데에 톱니 하나가 온다. */
    static float[] gearOutline() {
        float step = 360f / GEAR_TEETH, top = 8f, base = 12.5f;
        java.util.List<float[]> points = new java.util.ArrayList<>();
        for (int i = 0; i < GEAR_TEETH; i++) {
            float c = -90f + i * step;
            arc(points, GEAR_ROOT, c - step / 2f, c - base, 3);
            arc(points, GEAR_OUTER, c - top, c + top, 4);
            arc(points, GEAR_ROOT, c + base, c + step / 2f, 3);
        }
        float[] out = new float[points.size() * 2];
        for (int i = 0; i < points.size(); i++) { out[i * 2] = points.get(i)[0]; out[i * 2 + 1] = points.get(i)[1]; }
        return out;
    }

    private static void arc(java.util.List<float[]> points, float radius, float from, float to, int segments) {
        for (int s = 0; s <= segments; s++) {
            double a = Math.toRadians(from + (to - from) * s / segments);
            float x = (float) (12 + radius * Math.cos(a)), y = (float) (12 + radius * Math.sin(a));
            if (!points.isEmpty()) {
                float[] last = points.get(points.size() - 1);
                if (Math.abs(last[0] - x) < 1e-4f && Math.abs(last[1] - y) < 1e-4f) continue;
            }
            points.add(new float[]{x, y});
        }
    }
}
