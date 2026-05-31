package com.kingzcheung.xime.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 简易计算器引擎
 *
 * 状态机：
 * - 空闲: 等待数字或运算符
 * - 已输入左操作数: 可继续输入数字或运算符
 * - 已输入运算符: 等待右操作数
 * - 活跃 (有右操作数): 实时显示计算结果在候选栏
 *
 * 触发计算器：用户在输入数字后按了 + - * / 中的一个
 * 候选栏显示：左操作数 + 运算符 + 右操作数 = 结果
 * 选择候选后：结果替换输入框内容
 *
 * 运算规则：
 * - 使用 BigDecimal 进行精确运算，避免浮点数精度问题
 * - 除法结果为无限小数时，以分数形式显示（如 "10/3"）
 * - 除法结果为有限小数时，以十进制形式显示（如 "0.25"）
 */
class CalculatorEngine {

    private var leftOperand = ""
    private var operator_ = ""
    private var rightOperand = ""

    /** 计算器是否处于活跃状态（即有完整表达式和结果） */
    fun isActive(): Boolean =
        leftOperand.isNotEmpty() && operator_.isNotEmpty() && rightOperand.isNotEmpty()

    /** 获取计算结果文本，为空表示无法计算（如除零） */
    fun getResult(): String {
        if (!isActive()) return ""
        return computeResult() ?: ""
    }

    /** 获取已在输入框中提交的表达式文本，如 "12+35" */
    fun getExpression(): String = "$leftOperand$operator_$rightOperand"

    /** 获取候选栏显示文本，如 "12 + 35 = 47"，null 表示不显示 */
    fun getCandidate(): String? {
        if (!isActive()) return null
        val result = getResult()
        if (result.isEmpty()) return null
        return "$leftOperand $operator_ $rightOperand = $result"
    }

    /** 清除所有状态 */
    fun clear() {
        leftOperand = ""
        operator_ = ""
        rightOperand = ""
    }

    /**
     * 处理数字或小数点输入
     * @return 计算器是否处于活跃状态（有候选结果）
     */
    fun handleDigit(input: String): Boolean {
        if (operator_.isEmpty()) {
            if (input == ".") {
                if (leftOperand.contains(".")) return false
                if (leftOperand.isEmpty()) leftOperand = "0."
                else leftOperand += "."
            } else {
                leftOperand += input
            }
            return false
        } else {
            if (input == ".") {
                if (rightOperand.contains(".")) return false
                if (rightOperand.isEmpty()) rightOperand = "0."
                else rightOperand += "."
            } else {
                rightOperand += input
            }
            return true
        }
    }

    /**
     * 处理运算符输入 (+ - * /)
     * 首次按运算符：保存并等待右操作数
     * 已有结果时按运算符：结果作为新的左操作数继续计算
     */
    fun handleOperator(op: String): Boolean {
        if (leftOperand.isEmpty()) return false

        if (isActive()) {
            // 链式计算：直接用数值计算结果作为新左操作数（避免分数"10/3"无法参与后续运算）
            val numericResult = computeNumericResult()
            if (numericResult != null) {
                leftOperand = numericResult
                operator_ = op
                rightOperand = ""
                return false
            }
        }

        operator_ = op
        rightOperand = ""
        return false
    }

    /**
     * 链式计算时使用高精度数值结果，避免分数字符串无法参与后续运算。
     */
    /**
     * 链式计算时使用高精度数值结果，避免分数字符串无法参与后续运算。
     * 使用 15 位精度确保链式运算如 10/3*3 能正确得到 10。
     */
    private fun computeNumericResult(): String? {
        val l = leftOperand.toBigDecimalOrNull() ?: return null
        val r = rightOperand.toBigDecimalOrNull() ?: return null
        return try {
            val result = when (operator_) {
                "+" -> l + r
                "-" -> l - r
                "*" -> l * r
                "/" -> {
                    if (r.compareTo(BigDecimal.ZERO) == 0) return null
                    l.divide(r, 15, RoundingMode.HALF_UP)
                }
                else -> return null
            }
            formatBigDecimal(result.stripTrailingZeros())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 处理退格
     * @return 计算器是否仍处于活跃状态
     */
    fun handleDelete(): Boolean {
        if (rightOperand.isNotEmpty()) {
            rightOperand = rightOperand.dropLast(1)
        } else if (operator_.isNotEmpty()) {
            operator_ = ""
        } else if (leftOperand.isNotEmpty()) {
            leftOperand = leftOperand.dropLast(1)
        }
        return isActive()
    }

    /**
     * 直接计算表达式，独立于内部状态机。
     * 用于测试和外部直接调用。
     *
     * @param left 左操作数字符串，如 "10", "-5", "3.14"
     * @param op 运算符 + - * /
     * @param right 右操作数字符串
     * @return 结果字符串，null 表示计算错误（格式错误、除零等）
     */
    fun calculate(left: String, op: String, right: String): String? {
        val l = left.toBigDecimalOrNull() ?: return null
        val r = right.toBigDecimalOrNull() ?: return null
        return try {
            when (op) {
                "+" -> formatBigDecimal(l + r)
                "-" -> formatBigDecimal(l - r)
                "*" -> formatBigDecimal(l * r)
                "/" -> {
                    if (r.compareTo(BigDecimal.ZERO) == 0) return null
                    try {
                        // 尝试精确除法
                        formatBigDecimal(l.divide(r))
                    } catch (_: ArithmeticException) {
                        // 无限小数，以分数形式显示
                        "$left/$right"
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 计算结果，返回格式化后的字符串。
     * @return null 表示无法计算
     */
    private fun computeResult(): String? {
        if (leftOperand.isEmpty() || operator_.isEmpty() || rightOperand.isEmpty()) return null
        return calculate(leftOperand, operator_, rightOperand)
    }

    /**
     * 格式化 BigDecimal：
     * - 限制最多 10 位小数，避免浮点运算的尾数误差
     * - 接近整数的值（如 9.9999999999）四舍五入为整数
     * - 整数去掉 ".0" 后缀
     * - 去掉尾部多余零
     */
    private fun formatBigDecimal(value: BigDecimal): String {
        val rounded = value.setScale(10, RoundingMode.HALF_UP)
        // 检查是否接近整数（处理 9.9999999999 ≈ 10 等链式运算尾数问题）
        val intPart = rounded.setScale(0, RoundingMode.HALF_UP)
        if (rounded.subtract(intPart).abs() < BigDecimal("0.000000001")) {
            return intPart.stripTrailingZeros().toPlainString()
        }
        val stripped = rounded.stripTrailingZeros()
        return stripped.toPlainString()
    }
}
