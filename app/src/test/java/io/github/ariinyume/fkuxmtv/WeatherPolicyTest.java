package io.github.ariinyume.fkuxmtv;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherPolicyTest {
    @Test public void staleAndFutureReadingsAreNotShown(){
        long now=10_000_000L;assertTrue(WeatherPolicy.fresh(now-1000,now,WeatherPolicy.MAX_AGE));
        assertFalse(WeatherPolicy.fresh(now-WeatherPolicy.MAX_AGE,now,WeatherPolicy.MAX_AGE));
        assertFalse(WeatherPolicy.fresh(now+1,now,WeatherPolicy.MAX_AGE));assertFalse(WeatherPolicy.fresh(0,now,WeatherPolicy.MAX_AGE));
    }
    @Test public void rejectMissingOrImpossibleWeather(){assertFalse(WeatherPolicy.valid(Double.NaN,0));assertFalse(WeatherPolicy.valid(120,0));assertFalse(WeatherPolicy.valid(22,999));assertTrue(WeatherPolicy.valid(-35.5,71));}
    @Test public void coordinatesMustBeFiniteAndOnEarth(){assertTrue(WeatherPolicy.coordinates(39.9,116.4));assertFalse(WeatherPolicy.coordinates(91,0));assertFalse(WeatherPolicy.coordinates(0,181));assertFalse(WeatherPolicy.coordinates(Double.NaN,0));}
    @Test public void allWmoConditionsHaveMatchingIcons(){int[] codes={0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99};for(int code:codes){assertFalse(WeatherPolicy.description(code).isEmpty());assertNotNull(WeatherPolicy.icon(code,true));}assertEquals("moon",WeatherPolicy.icon(0,false));assertEquals("snow",WeatherPolicy.icon(86,true));assertEquals("storm",WeatherPolicy.icon(99,true));}
}
