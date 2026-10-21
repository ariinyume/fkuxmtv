package io.github.ariinyume.fkuxmtv;

/** Keep the selected tile fully within the fixed shelf without shrinking its icon. */
final class DockScroll {
    static int reveal(int current, int left, int right, int viewport, int content) {
        int maximum=Math.max(0,content-viewport);
        int target=Math.max(0,Math.min(current,maximum));
        if(left<target)target=left;
        else if(right>target+viewport)target=right-viewport;
        return Math.max(0,Math.min(target,maximum));
    }
}
