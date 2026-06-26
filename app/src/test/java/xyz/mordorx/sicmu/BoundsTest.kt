package xyz.mordorx.sicmu

import org.junit.Test
import xyz.mordorx.sicmu.data.bounds
import kotlin.random.Random
import kotlin.random.nextLong

class BoundsTest {

    val telephoneNumbers = listOf(
        "+49 1238531 3198031",
        "+1 0800 981513313",
        "+49 13383 1383833883",
        "+1 8313590 13351398398333",
        "+12 39313583 11125153412",
        "+13 393383 111213551412",
        "+144 339383 1112515412",
        )
    @Test
    fun testBounds() {
        val countryCodes = listOf("+1", "+49", "+123")
        val nums = buildList {
            for (i in 0..100000) {
                val country = countryCodes.random()
                val area = Random.nextInt(10000, 99999)
                val direct = Random.nextLong(1000000, 9999999)
                val num = "$country $area $direct"
                add(num)
            }
        }.sorted()

        val predicate = { s: String -> s.startsWith("+49 ") }
        val predicateReverse = { s: String -> !predicate(s) }

        // Iter
        var a = System.currentTimeMillis()
        val countIter = nums.dropWhile(predicateReverse).takeWhile(predicate).count()
        var b = System.currentTimeMillis()
        println("countIter=$countIter (${b-a}Ms)")

        // Seq Iter
        a = System.currentTimeMillis()
        val countSeqIter = nums.asSequence().dropWhile(predicateReverse).takeWhile(predicate).count()
        b = System.currentTimeMillis()
        println("countSeqIter=$countSeqIter (${b-a}Ms)")

        // Bounds
        a = System.currentTimeMillis()
        val bounds = bounds(nums, predicate)
        val countBounds = nums.subList(bounds.first, bounds.last).count()
        b = System.currentTimeMillis()
        println("countBounds=$countBounds (${b-a}Ms)")


        assert(true)
    }
}