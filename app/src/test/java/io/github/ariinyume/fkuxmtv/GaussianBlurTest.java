package io.github.ariinyume.fkuxmtv;
import org.junit.Test;
import static org.junit.Assert.*;
public class GaussianBlurTest {
    @Test public void flatFieldRetainsColorIncludingEdges(){
        int[] input=new int[35];java.util.Arrays.fill(input,0xff739ac1);
        assertArrayEquals(input,GaussianBlur.apply(input,7,5,4));
    }
    @Test public void impulseSpreadsSymmetricallyWithoutHardEdges(){
        int[] input=new int[81];java.util.Arrays.fill(input,0xff000000);input[40]=0xffffffff;
        int[] out=GaussianBlur.apply(input,9,9,1);
        assertTrue((out[40]&255)>0);assertTrue((out[40]&255)<255);
        assertTrue((out[39]&255)>0);assertEquals(out[39],out[41]);assertEquals(out[31],out[49]);
        assertTrue((out[40]&255)>(out[39]&255));
    }
}
