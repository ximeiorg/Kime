package com.kingzcheung.xime.calculator

import org.junit.Assert.*
import org.junit.Test

class CalculatorEngineTest {

    // ===================== 状态机基础测试 =====================

    @Test
    fun `初始状态不活跃`() {
        val engine = CalculatorEngine()
        assertFalse(engine.isActive())
        assertNull(engine.getCandidate())
        assertEquals("", engine.getResult())
    }

    @Test
    fun `只输入数字不触发计算器`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("2")
        assertFalse(engine.isActive())
        assertNull(engine.getCandidate())
    }

    @Test
    fun `数字后按运算符等待右操作数`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("2")
        engine.handleOperator("+")
        assertFalse(engine.isActive())
        assertNull(engine.getCandidate())
    }

    @Test
    fun `简单加法状态机`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("2")
        engine.handleOperator("+")
        engine.handleDigit("3")
        assertTrue(engine.isActive())
        assertEquals("12 + 3 = 15", engine.getCandidate())
        assertEquals("15", engine.getResult())
    }

    @Test
    fun `多位数字加法状态机`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("2")
        engine.handleOperator("+")
        engine.handleDigit("3")
        engine.handleDigit("5")
        assertTrue(engine.isActive())
        assertEquals("12 + 35 = 47", engine.getCandidate())
        assertEquals("47", engine.getResult())
    }

    @Test
    fun `连续运算链式状态机`() {
        val engine = CalculatorEngine()
        engine.handleDigit("2")
        engine.handleOperator("+")
        engine.handleDigit("3")
        assertEquals("2 + 3 = 5", engine.getCandidate())

        engine.handleOperator("*")
        engine.handleDigit("4")
        assertEquals("5 * 4 = 20", engine.getCandidate())
        assertEquals("20", engine.getResult())
    }

    @Test
    fun `退格删除右操作数`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("0")
        engine.handleOperator("+")
        engine.handleDigit("5")
        engine.handleDigit("5")
        assertEquals("10 + 55 = 65", engine.getCandidate())

        engine.handleDelete()
        assertEquals("10 + 5 = 15", engine.getCandidate())

        engine.handleDelete()
        assertNull(engine.getCandidate())
    }

    @Test
    fun `退格删除运算符回到左操作数`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("0")
        engine.handleOperator("+")

        engine.handleDelete()
        assertFalse(engine.isActive())
        assertNull(engine.getCandidate())
        assertEquals("10", engine.getExpression())
    }

    @Test
    fun `退格删除左操作数`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("0")

        engine.handleDelete()
        assertEquals("1", engine.getExpression())

        engine.handleDelete()
        assertEquals("", engine.getExpression())
    }

    @Test
    fun `清除状态`() {
        val engine = CalculatorEngine()
        engine.handleDigit("5")
        engine.handleOperator("+")
        engine.handleDigit("3")
        assertTrue(engine.isActive())

        engine.clear()
        assertFalse(engine.isActive())
        assertNull(engine.getCandidate())
        assertEquals("", engine.getExpression())
    }

    @Test
    fun `同一操作数多个小数点不重复添加`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit(".")
        engine.handleDigit(".")
        engine.handleDigit("5")
        assertEquals("1.5", engine.getExpression())
    }

    @Test
    fun `小数计算状态机`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit(".")
        engine.handleDigit("5")
        engine.handleOperator("*")
        engine.handleDigit("2")
        assertEquals("1.5 * 2 = 3", engine.getCandidate())
        assertEquals("3", engine.getResult())
    }

    @Test
    fun `先按运算符无效`() {
        val engine = CalculatorEngine()
        engine.handleOperator("+")
        assertFalse(engine.isActive())
        assertEquals("", engine.getExpression())
    }

    @Test
    fun `除法分数结果状态机`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("0")
        engine.handleOperator("/")
        engine.handleDigit("3")
        assertTrue(engine.isActive())
        assertEquals("10 / 3 = 10/3", engine.getCandidate())
        assertEquals("10/3", engine.getResult())
    }

    @Test
    fun `除数为零返回空`() {
        val engine = CalculatorEngine()
        engine.handleDigit("5")
        engine.handleOperator("/")
        engine.handleDigit("0")
        assertEquals("", engine.getResult())
        assertNull(engine.getCandidate())
    }

    // ===================== 直接计算测试 calculate() =====================
    //
    // 测试表格：操作数1 | 运算符 | 操作数2 | 预期结果

    @Test
    fun `加法 1+1=2`() = assertCalculate("1", "+", "1", "2")
    @Test
    fun `加法 0+0=0`() = assertCalculate("0", "+", "0", "0")
    @Test
    fun `加法 -5+5=0`() = assertCalculate("-5", "+", "5", "0")
    @Test
    fun `加法 -3+-7=-10`() = assertCalculate("-3", "+", "-7", "-10")
    @Test
    fun `加法 2_5+3_5=6`() = assertCalculate("2.5", "+", "3.5", "6")
    @Test
    fun `加法 0_1+0_2=0_3`() = assertCalculate("0.1", "+", "0.2", "0.3")
    @Test
    fun `加法 1000+999=1999`() = assertCalculate("1000", "+", "999", "1999")
    @Test
    fun `加法 -2_4+1_6=-0_8`() = assertCalculate("-2.4", "+", "1.6", "-0.8")
    @Test
    fun `加法 123456789+987654321=1111111110`() = assertCalculate("123456789", "+", "987654321", "1111111110")
    @Test
    fun `加法 1_25+2_75=4`() = assertCalculate("1.25", "+", "2.75", "4")
    @Test
    fun `加法 0_333+0_667=1`() = assertCalculate("0.333", "+", "0.667", "1")
    @Test
    fun `加法 -0_5+0_5=0`() = assertCalculate("-0.5", "+", "0.5", "0")
    @Test
    fun `加法 999999999999+1=1000000000000`() = assertCalculate("999999999999", "+", "1", "1000000000000")
    @Test
    fun `加法 0_001+0_002=0_003`() = assertCalculate("0.001", "+", "0.002", "0.003")
    @Test
    fun `加法 -100+-200=-300`() = assertCalculate("-100", "+", "-200", "-300")
    @Test
    fun `加法 2_71828+3_14159=5_85987`() = assertCalculate("2.71828", "+", "3.14159", "5.85987")
    @Test
    fun `加法 0+-99_9=-99_9`() = assertCalculate("0", "+", "-99.9", "-99.9")
    @Test
    fun `加法 7_7+2_3=10`() = assertCalculate("7.7", "+", "2.3", "10")
    @Test
    fun `加法 -15_25+7_75=-7_5`() = assertCalculate("-15.25", "+", "7.75", "-7.5")
    @Test
    fun `加法 98765+43210=141975`() = assertCalculate("98765", "+", "43210", "141975")
    @Test
    fun `加法 3_5+-3_5=0`() = assertCalculate("3.5", "+", "-3.5", "0")
    @Test
    fun `加法 12_345+0_005=12_35`() = assertCalculate("12.345", "+", "0.005", "12.35")
    @Test
    fun `加法 -110+110=0`() = assertCalculate("-110", "+", "110", "0")
    @Test
    fun `加法 0_0001+0_0002=0_0003`() = assertCalculate("0.0001", "+", "0.0002", "0.0003")
    @Test
    fun `加法 2147483647+1=2147483648`() = assertCalculate("2147483647", "+", "1", "2147483648")

    @Test
    fun `减法 5-2=3`() = assertCalculate("5", "-", "2", "3")
    @Test
    fun `减法 2-5=-3`() = assertCalculate("2", "-", "5", "-3")
    @Test
    fun `减法 -5-5=-10`() = assertCalculate("-5", "-", "5", "-10")
    @Test
    fun `减法 -5--5=0`() = assertCalculate("-5", "-", "-5", "0")
    @Test
    fun `减法 0-7=-7`() = assertCalculate("0", "-", "7", "-7")
    @Test
    fun `减法 7-0=7`() = assertCalculate("7", "-", "0", "7")
    @Test
    fun `减法 3_14-1_14=2`() = assertCalculate("3.14", "-", "1.14", "2")
    @Test
    fun `减法 10-9_99=0_01`() = assertCalculate("10", "-", "9.99", "0.01")
    @Test
    fun `减法 0_3-0_1=0_2`() = assertCalculate("0.3", "-", "0.1", "0.2")
    @Test
    fun `减法 -2_5-3_7=-6_2`() = assertCalculate("-2.5", "-", "3.7", "-6.2")
    @Test
    fun `减法 2_5--3_7=6_2`() = assertCalculate("2.5", "-", "-3.7", "6.2")
    @Test
    fun `减法 -1_1--2_2=1_1`() = assertCalculate("-1.1", "-", "-2.2", "1.1")
    @Test
    fun `减法 123_456-23_456=100`() = assertCalculate("123.456", "-", "23.456", "100")
    @Test
    fun `减法 0-0=0`() = assertCalculate("0", "-", "0", "0")
    @Test
    fun `减法 8_88-8_88=0`() = assertCalculate("8.88", "-", "8.88", "0")
    @Test
    fun `减法 -100-100=-200`() = assertCalculate("-100", "-", "100", "-200")
    @Test
    fun `减法 999999999-999999998=1`() = assertCalculate("999999999", "-", "999999998", "1")
    @Test
    fun `减法 0_0001-0_00005=0_00005`() = assertCalculate("0.0001", "-", "0.00005", "0.00005")
    @Test
    fun `减法 -7--7=0`() = assertCalculate("-7", "-", "-7", "0")
    @Test
    fun `减法 15_75-3_25=12_5`() = assertCalculate("15.75", "-", "3.25", "12.5")
    @Test
    fun `减法 200-300=-100`() = assertCalculate("200", "-", "300", "-100")
    @Test
    fun `减法 -200--300=100`() = assertCalculate("-200", "-", "-300", "100")
    @Test
    fun `减法 150-50=100`() = assertCalculate("150", "-", "50", "100")
    @Test
    fun `减法 0_999-1=-0_001`() = assertCalculate("0.999", "-", "1", "-0.001")
    @Test
    fun `减法 2147483647-2147483647=0`() = assertCalculate("2147483647", "-", "2147483647", "0")

    @Test
    fun `乘法 2*3=6`() = assertCalculate("2", "*", "3", "6")
    @Test
    fun `乘法 -2*3=-6`() = assertCalculate("-2", "*", "3", "-6")
    @Test
    fun `乘法 -2*-3=6`() = assertCalculate("-2", "*", "-3", "6")
    @Test
    fun `乘法 0*999=0`() = assertCalculate("0", "*", "999", "0")
    @Test
    fun `乘法 0_5*0_5=0_25`() = assertCalculate("0.5", "*", "0.5", "0.25")
    @Test
    fun `乘法 0_1*0_2=0_02`() = assertCalculate("0.1", "*", "0.2", "0.02")
    @Test
    fun `乘法 2_5*4=10`() = assertCalculate("2.5", "*", "4", "10")
    @Test
    fun `乘法 -1_2*3=-3_6`() = assertCalculate("-1.2", "*", "3", "-3.6")
    @Test
    fun `乘法 -1_2*-3=3_6`() = assertCalculate("-1.2", "*", "-3", "3.6")
    @Test
    fun `乘法 7*0=0`() = assertCalculate("7", "*", "0", "0")
    @Test
    fun `乘法 12345*6789=83810205`() = assertCalculate("12345", "*", "6789", "83810205")
    @Test
    fun `乘法 1_25*0_8=1`() = assertCalculate("1.25", "*", "0.8", "1")
    @Test
    fun `乘法 -0_001*1000=-1`() = assertCalculate("-0.001", "*", "1000", "-1")
    @Test
    fun `乘法 3_33*2=6_66`() = assertCalculate("3.33", "*", "2", "6.66")
    @Test
    fun `乘法 -7_5*-2=15`() = assertCalculate("-7.5", "*", "-2", "15")
    @Test
    fun `乘法 0_125*8=1`() = assertCalculate("0.125", "*", "8", "1")
    @Test
    fun `乘法 999*999=998001`() = assertCalculate("999", "*", "999", "998001")
    @Test
    fun `乘法 -100*0_01=-1`() = assertCalculate("-100", "*", "0.01", "-1")
    @Test
    fun `乘法 12*12=144`() = assertCalculate("12", "*", "12", "144")
    @Test
    fun `乘法 1_1*1_1=1_21`() = assertCalculate("1.1", "*", "1.1", "1.21")
    @Test
    fun `乘法 -3_4*2_1=-7_14`() = assertCalculate("-3.4", "*", "2.1", "-7.14")
    @Test
    fun `乘法 2147483647*1=2147483647`() = assertCalculate("2147483647", "*", "1", "2147483647")
    @Test
    fun `乘法 2147483647*2=4294967294`() = assertCalculate("2147483647", "*", "2", "4294967294")
    @Test
    fun `乘法 0_0002*0_0003=0_00000006`() = assertCalculate("0.0002", "*", "0.0003", "0.00000006")
    @Test
    fun `乘法 9_999*1000=9999`() = assertCalculate("9.999", "*", "1000", "9999")

    @Test
    fun `除法 6除以3得2`() = assertCalculate("6", "/", "3", "2")
    @Test
    fun `除法 7除以2得3_5`() = assertCalculate("7", "/", "2", "3.5")
    @Test
    fun `除法 -6除以3得-2`() = assertCalculate("-6", "/", "3", "-2")
    @Test
    fun `除法 6除以-3得-2`() = assertCalculate("6", "/", "-3", "-2")
    @Test
    fun `除法 -6除以-3得2`() = assertCalculate("-6", "/", "-3", "2")
    @Test
    fun `除法 0除以5得0`() = assertCalculate("0", "/", "5", "0")
    @Test
    fun `除法 5除以0报错`() = assertNull(CalculatorEngine().calculate("5", "/", "0"))
    @Test
    fun `除法 0除以0报错`() = assertNull(CalculatorEngine().calculate("0", "/", "0"))
    @Test
    fun `除法 10除以3得10除以3`() = assertCalculate("10", "/", "3", "10/3")
    @Test
    fun `除法 1除以4得0_25`() = assertCalculate("1", "/", "4", "0.25")
    @Test
    fun `除法 1除以8得0_125`() = assertCalculate("1", "/", "8", "0.125")
    @Test
    fun `除法 7_5除以2_5得3`() = assertCalculate("7.5", "/", "2.5", "3")
    @Test
    fun `除法 0_3除以0_1得3`() = assertCalculate("0.3", "/", "0.1", "3")
    @Test
    fun `除法 -7_2除以3_6得-2`() = assertCalculate("-7.2", "/", "3.6", "-2")
    @Test
    fun `除法 9_99除以3_33得3`() = assertCalculate("9.99", "/", "3.33", "3")
    @Test
    fun `除法 22除以7得22除以7`() = assertCalculate("22", "/", "7", "22/7")
    @Test
    fun `除法 12345除以5得2469`() = assertCalculate("12345", "/", "5", "2469")
    @Test
    fun `除法 100除以3得100除以3`() = assertCalculate("100", "/", "3", "100/3")
    @Test
    fun `除法 0_005除以0_002得2_5`() = assertCalculate("0.005", "/", "0.002", "2.5")
    @Test
    fun `除法 -0_004除以0_002得-2`() = assertCalculate("-0.004", "/", "0.002", "-2")
    @Test
    fun `除法 1除以3得1除以3`() = assertCalculate("1", "/", "3", "1/3")
    @Test
    fun `除法 -1除以3得-1除以3`() = assertCalculate("-1", "/", "3", "-1/3")
    @Test
    fun `除法 2除以0_5得4`() = assertCalculate("2", "/", "0.5", "4")
    @Test
    fun `除法 1_21除以1_1得1_1`() = assertCalculate("1.21", "/", "1.1", "1.1")
    @Test
    fun `除法 999999999除以3得333333333`() = assertCalculate("999999999", "/", "3", "333333333")

    // ===================== 非法输入测试 =====================

    @Test
    fun `非法左操作数返回null`() {
        assertNull(CalculatorEngine().calculate("abc", "+", "1"))
    }

    @Test
    fun `非法右操作数返回null`() {
        assertNull(CalculatorEngine().calculate("1", "+", "xyz"))
    }

    @Test
    fun `非法运算符返回null`() {
        assertNull(CalculatorEngine().calculate("1", "%", "2"))
    }

    @Test
    fun `空字符串返回null`() {
        assertNull(CalculatorEngine().calculate("", "+", "1"))
    }

    // ===================== 候选栏显示格式测试 =====================

    @Test
    fun `候选栏显示分数`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleOperator("/")
        engine.handleDigit("3")
        assertEquals("1 / 3 = 1/3", engine.getCandidate())
    }

    @Test
    fun `候选栏显示整数结果`() {
        val engine = CalculatorEngine()
        engine.handleDigit("6")
        engine.handleOperator("/")
        engine.handleDigit("2")
        assertEquals("6 / 2 = 3", engine.getCandidate())
    }

    @Test
    fun `候选栏显示小数结果`() {
        val engine = CalculatorEngine()
        engine.handleDigit("7")
        engine.handleOperator("/")
        engine.handleDigit("2")
        assertEquals("7 / 2 = 3.5", engine.getCandidate())
    }

    // ===================== 链式计算测试 =====================

    @Test
    fun `链式计算 5+3=8 再 *2=16`() {
        val engine = CalculatorEngine()
        engine.handleDigit("5")
        engine.handleOperator("+")
        engine.handleDigit("3")
        assertEquals("5 + 3 = 8", engine.getCandidate())

        engine.handleOperator("*")
        engine.handleDigit("2")
        assertEquals("8 * 2 = 16", engine.getCandidate())
        assertEquals("16", engine.getResult())
    }

    @Test
    fun `链式计算 10除以3再乘3得10`() {
        val engine = CalculatorEngine()
        engine.handleDigit("1")
        engine.handleDigit("0")
        engine.handleOperator("/")
        engine.handleDigit("3")
        assertEquals("10 / 3 = 10/3", engine.getCandidate())

        // 链式计算时，左操作数变为数值（高精度3.3333333333），而非分数字符串
        engine.handleOperator("*")
        engine.handleDigit("3")
        val candidate = engine.getCandidate()
        assertNotNull(candidate)
        // 结果应为 10
        assertEquals("10", engine.getResult())
        // 候选格式可能为 "3.3333333333 * 3 = 10"
        assertTrue("候选应包含 * 3 = 10，实际: $candidate", candidate!!.endsWith(" * 3 = 10"))
    }

    // ===================== 辅助方法 =====================

    private fun assertCalculate(left: String, op: String, right: String, expected: String) {
        val result = CalculatorEngine().calculate(left, op, right)
        assertEquals("calculate(\"$left\", \"$op\", \"$right\") 应返回 \"$expected\"",
            expected, result)
    }
}
