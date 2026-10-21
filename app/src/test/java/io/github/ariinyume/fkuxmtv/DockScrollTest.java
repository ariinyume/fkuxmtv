package io.github.ariinyume.fkuxmtv;
import org.junit.Test;
import static org.junit.Assert.*;
public class DockScrollTest {
    @Test public void sixFitAndNineKeepTheFocusedTileVisibleInBothDirections(){
        assertEquals(0,DockScroll.reveal(0,710,842,860,842));
        int offset=0;
        for(int i=0;i<9;i++){
            int left=i*142,right=left+132;
            offset=DockScroll.reveal(offset,left,right,860,1268);
            assertTrue(left>=offset);assertTrue(right<=offset+860);
        }
        assertEquals(408,offset);
        for(int i=8;i>=0;i--){
            int left=i*142,right=left+132;
            offset=DockScroll.reveal(offset,left,right,860,1268);
            assertTrue(left>=offset);assertTrue(right<=offset+860);
        }
        assertEquals(0,offset);
    }
    @Test public void restoringLastItemAndShrinkingListClampsScroll(){
        assertEquals(408,DockScroll.reveal(0,1136,1268,860,1268));
        assertEquals(0,DockScroll.reveal(408,710,842,860,842));
    }
}
