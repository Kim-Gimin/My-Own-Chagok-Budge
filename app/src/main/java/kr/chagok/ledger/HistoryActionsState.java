package kr.chagok.ledger;

/** Manual collapse survives idle/resume; scrolling only hides expanded actions temporarily. */
final class HistoryActionsState {
    private boolean expanded,scrolling;
    HistoryActionsState(boolean expanded){this.expanded=expanded;}
    boolean expanded(){return expanded;}
    boolean visible(){return expanded&&!scrolling;}
    void toggle(){expanded=!expanded;scrolling=false;}
    void beginScroll(){scrolling=true;}
    void endScroll(){scrolling=false;}
}
