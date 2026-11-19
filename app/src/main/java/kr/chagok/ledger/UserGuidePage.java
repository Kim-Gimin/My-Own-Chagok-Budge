package kr.chagok.ledger;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Read-only, offline help. It does not receive a database or write preferences. */
final class UserGuidePage {
    private final Context context;
    private final int surface, ink, muted, accent;
    private final Typeface font;

    UserGuidePage(Context context, int surface, int ink, int muted, int accent, Typeface font) {
        this.context=context; this.surface=surface; this.ink=ink; this.muted=muted;
        this.accent=accent; this.font=font;
    }

    void populate(LinearLayout page, ScrollView scroll, int selected, IntConsumer changed) {
        TextView intro=text("차곡가계부 사용 설명서",21,ink,true);
        heading(intro); page.addView(intro);
        TextView hint=text("궁금한 항목을 눌러 펼쳐 보세요. 인터넷 없이 읽을 수 있고, 설명을 읽는 동안 기록은 바뀌지 않아요.",14,muted,false);
        hint.setPadding(0,dp(10),0,dp(20)); page.addView(hint);
        String[][][] topics=topics();
        LinearLayout[] cards=new LinearLayout[topics.length], bodies=new LinearLayout[topics.length];
        TextView[] buttons=new TextView[topics.length];
        final int[] open={selected>=0&&selected<topics.length?selected:-1};
        Runnable refresh=()->{
            for(int i=0;i<topics.length;i++) {
                boolean active=i==open[0]; bodies[i].setVisibility(active?View.VISIBLE:View.GONE);
                buttons[i].setText(topics[i][0][0]+(active?"  −":"  +"));
                buttons[i].setSelected(active);
                buttons[i].setContentDescription(topics[i][0][0]+(active?" 접기":" 펼치기"));
            }
        };
        for(int i=0;i<topics.length;i++) {
            final int index=i;
            LinearLayout card=new LinearLayout(context); card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable background=new GradientDrawable(); background.setColor(surface); background.setCornerRadius(dp(18));
            card.setBackground(background); cards[i]=card;
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.bottomMargin=dp(12); page.addView(card,params);
            TextView button=text("",16,accent,true); button.setPadding(dp(18),dp(17),dp(18),dp(17));
            button.setMinHeight(dp(56)); button.setGravity(Gravity.CENTER_VERTICAL); button.setFocusable(true);
            button.setAccessibilityDelegate(new View.AccessibilityDelegate(){
                @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host,info); info.setClassName("android.widget.Button");
                }
            });
            heading(button); buttons[i]=button; card.addView(button,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout body=new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(18),0,dp(18),dp(18)); bodies[i]=body; card.addView(body);
            for(int j=1;j<topics[i].length;j++) {
                TextView subtitle=text(topics[i][j][0],15,ink,true); subtitle.setPadding(0,dp(j==1?0:18),0,dp(7));
                heading(subtitle); body.addView(subtitle);
                TextView description=text(topics[i][j][1],14,ink,false); description.setTextIsSelectable(true); body.addView(description);
            }
            button.setOnClickListener(v->{
                open[0]=open[0]==index?-1:index; changed.accept(open[0]); refresh.run();
                scroll.post(()->scroll.smoothScrollTo(0,cards[index].getTop()));
            });
        }
        refresh.run();
        if(open[0]>=0)scroll.post(()->scroll.scrollTo(0,cards[open[0]].getTop()));
    }

    private int dp(float value){return Math.round(context.getResources().getDisplayMetrics().density*value);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView view=new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create(font,bold?Typeface.BOLD:Typeface.NORMAL)); view.setLineSpacing(dp(4),1);
        return view;
    }
    private void heading(TextView view){if(Build.VERSION.SDK_INT>=28)view.setAccessibilityHeading(true);}

    private static String[][][] topics(){return new String[][][]{
        {{"1. 처음 시작하기·기록 입력"},
         {"시작할 때 이미 갖고 있던 돈", "가계부 시작일에 보유한 현금·저축·투자는 ‘기초자산·현재 보유’에 등록해요. 시작일 이후 납입과 소비는 일반 내역에 기록해요. 같은 돈을 양쪽에 중복 입력하지 마세요."},
         {"지출 한 건 입력하기", "+ 버튼 → 지출 금액 입력 → 적용 → 새 기록에서 상세 분류·메모 확인 → 저장 순서예요. 첫 화면에서 수입·저축·투자·저축회수로 바꿀 수도 있어요. 금액은 양수로 입력해요."},
         {"달력에서 입력하기", "날짜를 누르면 그날의 내역을 확인해요. 오른쪽 아래 + 버튼을 누르면 선택한 날짜로 기록해요. 달력을 좌우로 밀어 월을 바꾸고, 연월을 눌러 이동할 수 있어요."},
         {"수정·삭제·복사", "내역을 눌러 수정하거나 삭제할 수 있어요. 자주 쓰는 기록과 복사 기능으로 반복 입력을 줄여요. 해지·배분으로 만든 연결 내역은 이동 전체를 수정하는 화면으로 열려요."}},
        {{"2. 수입·지출·저축·투자·저축회수"},
         {"수입과 지출", "수입은 월급·이자·정부기여금처럼 새로 들어온 돈이에요. 지출은 식비·월세처럼 소비한 돈이에요. 저축 원금을 되찾는 것은 새 수입이 아니에요."},
         {"저축과 투자", "저축은 적금·예금에 넣은 돈, 투자는 주식·ETF 등에 넣은 돈이에요. 둘 다 사용 가능 금액에서 차감하지만 소비 지출과는 따로 집계해요."},
         {"저축회수란?", "이미 저축한 원금을 꺼내는 기록이에요. 사용 가능 금액은 늘고, 해당 저축 분류의 보유 금액과 순저축은 줄어요. 예: 적금 원금 100만 원을 찾으면 ‘저축회수 100만 원’, 이자 2만 원은 별도 ‘수입 2만 원’이에요."},
         {"금액을 계산하는 방법", "기간 합계는 수입 − 지출 − 순저축 − 투자예요. 순저축은 저축 납입 − 저축회수예요. 기간 중 회수가 많으면 순저축이 음수일 수도 있어요. 순저축은 현재 예금 잔액과 다른 값이에요."}},
        {{"3. 기초자산·현재 보유"},
         {"기초자산 입력", "메뉴 또는 설정 → 기초자산·현재 보유에서 현금·저축·투자를 등록해요. 모든 기초자산의 날짜를 같은 가계부 시작일로 맞추세요. 기초자산은 수입이나 올해 납입 합계에 더하지 않아요."},
         {"2026년부터 쓰는 경우", "해지 원금 1,000만 원 중 올해 납입이 350만 원이라면, 그전 원금 650만 원만 2026-01-01 기준 기초저축으로 입력해요. 올해 350만 원은 실제 납입 날짜의 저축 내역으로 남겨요. 시작일이 다르면 실제 시작일을 사용하세요."},
         {"분류 이름과 잔액", "기초저축, 납입, 원금 회수의 상세 분류는 모두 같은 이름을 사용하세요. 예: ‘예시적금’. 보유 금액은 기초자산과 시작일 이후 기록으로 계산해요. 현금은 실제 은행 잔고와 연결되지 않고, 투자 보유는 입력 원금 기준이며 주가·매도 손익을 반영하지 않아요."},
         {"삭제·수정이 막힐 때", "해지에 사용한 원금이나 이후 이동에 필요한 금액을 지우거나 줄이면 잔액이 틀어질 수 있어 저장을 막아요. 관련 해지·배분과 원래 납입을 함께 확인하세요. 기존 내역은 임의로 고치지 않아요."}},
        {{"4. 저축 해지·배분 사용하기"},
         {"입력 순서", "메뉴 또는 설정 → 저축 해지·배분 → 새 이동을 눌러요. 실제 해지 날짜, 기존 납입과 같은 저축 분류, 실제 원금, 이전부터 보유한 원금, 실제 수령액, 수령액에 포함된 정부기여금, 새 저축·투자 금액과 받는 분류를 입력해요."},
         {"이전 원금과 기초자산", "‘이전부터 보유한 원금’은 가계부 시작 전에 넣어 두었던 원금이에요. 이미 해당 기초자산을 등록했다면 ‘기초자산 함께 등록’을 끄세요. 켜면 해지한 연도의 1월 1일 기준으로 함께 만들어요. 가계부 시작일이 다르면 기초자산을 직접 등록한 뒤 끄세요."},
         {"자동으로 만들어지는 기록", "미리보기 → 확인하고 저장하면 실제 원금은 저축회수, 새 예금은 저축, 주식은 투자로 기록해요. 정부기여금은 수입이에요. 이자는 수령액 − 실제 원금 − 정부기여금으로 계산해 별도 수입에 넣고, 차액이 음수면 손실 지출로 기록해요."},
         {"배분 후 남거나 부족한 돈", "수령액 − 새 저축 − 투자만큼 사용 가능 금액이 변해요. 남은 돈을 소비했다면 실제 소비를 별도 지출로 기록하세요. 남은 금액 자체를 수입으로 다시 적지 않아요. 배분액이 수령액보다 많으면 부족분은 기존 사용 가능 금액에서 충당해요."},
         {"중복 입력 주의", "엑셀에 원금 회수·이자·정부기여금·재저축·투자를 직접 적었다면 앱에서 같은 해지·배분을 다시 저장하지 마세요. 자동 입력과 엑셀 수동 입력 중 한 가지 방법만 사용하세요."}},
        {{"5. 가상 적금 해지 예시"},
         {"원금과 수령액", "설명용 가상 금액이며 실제 사용자 사례가 아니에요.\n원금 10,000,000원\n올해 납입 3,500,000원\n이전 원금 6,500,000원\n이자 120,000원\n정부기여금 80,000원\n실제 수령액 10,200,000원"},
         {"먼저 준비할 기록", "기초자산에 2026-01-01 / 예시적금 / 6,500,000원 / 저축을 등록해요. 올해 납입 3,500,000원은 실제 날짜별 저축 내역에 남겨요. 이미 등록했다면 다시 추가하지 않아요."},
         {"앱에서 해지·배분 입력", "실제 원금 10,000,000원\n이전부터 보유한 원금 6,500,000원\n실제 수령액 10,200,000원\n정부기여금 80,000원\n예금 8,000,000원 / 주식 투자 1,500,000원\n기초자산을 먼저 등록했으므로 ‘기초자산 함께 등록’은 꺼요."},
         {"결과와 남은 돈 소비", "이 사건으로 기존 예시적금는 0원, 예금은 8,000,000원, 투자는 1,500,000원이 돼요. 배분 직후 현금 변화는 +700,000원이에요. 이 돈을 모두 썼다면 700,000원을 별도 지출로 기록하세요. 이미 개별 소비를 적었다면 또 적지 않아요. 전체 현금은 다른 수입·지출과 기초현금까지 합산한 값이에요."},
         {"가상 예시의 올해 순저축은 왜 150만 원인가요?", "올해 납입 3,500,000 − 원금 회수 10,000,000 + 예금 재가입 8,000,000 = 1,500,000원이에요. 이전 원금은 기초자산이므로 올해 납입에 더하지 않아요. 현재 예금 보유 800만 원과 올해 순저축 150만 원은 서로 다른 지표예요."}},
        {{"6. 엑셀로 한 번에 입력하기"},
         {"입력 양식과 열 순서", "엑셀 화면에서 입력 양식을 저장해요. ‘내역’ 시트의 열은 날짜 / 내역 / 상세 분류 / 금액 / 돈의 성격 / 고유ID 순서예요. 금액은 양수로 쓰고 돈의 성격에서 수입·지출·저축·투자·저축회수를 선택하세요. 앱이 돈의 성격에 따라 계산해요."},
         {"기초자산 시트에 이전 원금 쓰기", "날짜: 2026-01-01\n메모: 2025년까지 납입한 원금\n상세 분류: 예시적금\n금액: 6500000\n자산 종류: 저축\n고유ID: 새 입력이면 비워두기\n기초자산의 모든 날짜는 같은 가계부 시작일로 맞추세요."},
         {"해지를 엑셀로 직접 쓰는 예시", "해지 날짜의 ‘내역’ 시트에 아래 내용을 적어요.\n\n해지 이자 / 이자 / 120000 / 수입\n정부기여금 / 정부기여금 / 80000 / 수입\n해지원금 / 예시적금 / 10000000 / 저축회수\n예금 가입 / 예금 / 8000000 / 저축\n주식 투자 / 주식 / 1500000 / 투자\n\n남은 돈을 전부 소비했다면 실제 소비 날짜에 지출 700000원을 기록해요. 이미 적은 소비는 다시 쓰지 마세요. 정부기여금을 저축회수로 적으면 안 돼요."},
         {"예시와 고유ID", "‘입력예시’ 시트는 참고용이며 가져오지 않아요. 실제 추가할 행은 ‘내역’ 또는 ‘기초자산’에 적으세요. 새 내역의 고유ID는 비워도 돼요. 앱 백업의 기존 고유ID는 유지하세요. ID를 바꿔 같은 내역을 다시 넣으면 중복될 수 있어요."},
         {"가져오기 확인", "파일 선택 → 미리보기에서 추가·중복·오류 확인 → 확인 추가 순서예요. 오류가 있으면 수정 후 다시 가져오세요. 같은 ID에 다른 내용이 있으면 기존 기록을 덮어쓰지 않아요. 금액 수식 대신 실제 숫자를 입력하세요. 엑셀로 직접 쓴 해지 내역은 앱의 자동 해지·배분에 다시 입력하지 마세요."}},
        {{"7. 백업·복원·기기 변경"},
         {"전체 엑셀 백업", "엑셀 화면 → 전체 내역 백업으로 거래와 기초자산을 저장해요. 특정 월의 백업은 전체 거래를 담지 않으므로 장부 전체를 옮길 때는 전체 백업을 사용하세요."},
         {"계획 포함 ZIP 백업", "해지·배분 연결 정보와 예산·목표·반복·즐겨찾기까지 이어 쓰려면 계획 포함 ZIP을 사용하세요. XLSX만 복원하면 거래와 기초자산은 남지만 해지·배분 연결 정보는 복원되지 않아 일반 내역으로 다뤄요. PIN과 화면 설정은 ZIP에 포함되지 않아요."},
         {"안전하게 보관하기", "파일을 휴대폰 외의 별도 저장소에도 보관하고 열리는지 확인하세요. 앱은 은행이나 클라우드에 자동 동기화하지 않아요. 선택형 백업 알림은 백업할 때를 알려 주는 기능이며 자동 백업은 아니에요."},
         {"앱 업데이트·삭제", "이전 앱 위에 새 APK를 설치하면 업데이트해요. 앱 삭제나 저장공간의 데이터 삭제는 장부를 지울 수 있으니 먼저 전체 백업을 보관하세요. 새 기기에서는 백업 파일을 가져와 미리보기 후 복원하세요."}},
        {{"8. 내역·통계·결산 보기"},
         {"전체 내역과 검색", "내역 화면에서 전체 기록을 볼 수 있어요. 돋보기는 메모 검색, A–Z는 상세 분류 선택과 날짜순·분류순 보기예요. 스크롤 중에는 도구가 잠시 숨겨지고, 접기 버튼으로 직접 접거나 펼칠 수 있어요."},
         {"통계와 원형 차트", "월별·연도별·올해 누적·지정 기간으로 조회해요. 올해 누적은 1월 1일부터 오늘까지이며 미래 기록은 제외해요. 전체 비교와 유형별 차트를 볼 수 있고 원을 잡아 돌리면 비율 표시도 함께 움직여요. 분류나 원의 조각을 누르면 해당 내역을 확인해요."},
         {"회수가 있는 차트", "저축 통계는 납입에서 회수를 뺀 순저축이에요. 0 이하인 금액은 원형 조각으로 나타내기 어려워 차트에서 제외할 수 있지만, 내역과 합계에는 반영돼요."},
         {"결산과 홈", "결산은 연간·종합·기간 결산으로 월별 결과를 보여줘요. 홈 화면 커스텀에서 오늘·이번 주·이번 달·올해·전체의 표시 항목을 고를 수 있어요. 기간 합계와 현재 보유 금액을 구분해서 확인하세요."}},
        {{"9. 예산·목표·반복·즐겨찾기"},
         {"월 예산", "전체 지출 또는 상세 분류별 예산을 정하고 사용액·남은 금액을 확인해요. 선택 월 전용값과 매월 기본값을 설정할 수 있어요. 저축·투자·저축회수는 소비 지출 예산에 넣지 않아요."},
         {"저축·투자 목표와 추이", "납입 목표를 정하고 진행 상황을 확인해요. 월별 추이·전월 비교에서는 최근 6개월 또는 12개월의 네 유형을 함께 볼 수 있어요."},
         {"반복 기록", "주·월·연 단위로 예정 기록을 만들어요. 예정일이 오면 확인 후 실제 기록으로 저장하므로, 예정만으로 금액이 합계에 더해지지는 않아요. 한 회 건너뛰기·일시 중지·재개·삭제를 사용할 수 있어요."},
         {"자주 쓰는 기록", "즐겨찾는 입력 내용을 저장해 다음 기록에 활용해요. 실제 저장하기 전에 날짜·금액·분류·메모를 확인하세요."}},
        {{"10. 분류·화면·글꼴·잠금 설정"},
         {"상세 분류 추가·삭제", "기록 입력에서 분류를 추가하거나 설정 → 상세 분류 관리에서 삭제·복원할 수 있어요. 분류를 삭제해도 과거 거래와 합계는 유지해요. 저축회수는 저축과 같은 분류 목록을 사용해요."},
         {"시작 화면·다크 모드·글꼴", "설정에서 시작 화면과 홈 표시 항목을 선택해요. 다크 모드와 휴대폰 기본 글꼴·나눔고딕·나눔명조를 사용할 수 있어요. 설명서에도 앱의 색상과 글꼴이 적용돼요."},
         {"비밀번호", "4~12자리 숫자로 앱 잠금을 설정할 수 있어요. 현재 비밀번호를 확인해 변경·해제해요. 비밀번호를 잊으면 앱에서 복구할 수 없으므로 따로 기억하세요. 잠금 설정만으로 내보낸 백업 파일이 암호화되는 것은 아니에요."},
         {"앱 전체 초기화(포맷)", "설정 → 앱 전체 초기화(포맷)에서 장부·기초자산·해지/배분·계획·사용자 분류·화면·비밀번호·백업 알림 설정과 앱 내부 파일을 모두 삭제해요. ‘먼저 백업’으로 전체 엑셀 또는 계획 포함 ZIP을 저장할 수 있어요. 잠금 사용 중에는 현재 비밀번호를 확인하고, 마지막으로 ‘초기화’를 직접 입력해야 삭제돼요. 앱이 종료된 뒤 다시 열면 처음 상태예요. 앱 밖에 따로 저장한 백업 파일은 유지돼요. 백업이 없으면 삭제한 기록을 복구할 수 없어요."}}
    };}
}
