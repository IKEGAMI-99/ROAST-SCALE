package jp.ikegami.roastscale.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class HealthTrendsTest {
    private fun day(back: Int, hour: Int) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -back)
    }.timeInMillis
    private fun m(id: Long, date: Long, w: Double) = Measurement(id,date,w,null,null,"")
    @Test fun ratio() {
        assertEquals(0.5, HealthTrends.waistToHeight(85.0,170)!!,0.001)
        assertNull(HealthTrends.waistToHeight(85.0,0))
    }
    @Test fun meanExcludesOldDaysAndDuplicateWeighings() {
        val now = day(0,20)
        val rows = listOf(m(1,day(0,18),72.0),m(2,day(0,8),90.0),
            m(3,day(1,18),74.0),m(4,day(8,18),60.0))
        assertEquals(73.0,HealthTrends.weight7DayAverage(rows,now)!!,0.01)
    }
}
