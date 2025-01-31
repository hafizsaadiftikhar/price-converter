package com.minimalisticapps.priceconverter

import android.text.InputFilter
import android.text.Spanned
import java.util.regex.Matcher
import java.util.regex.Pattern

internal class DecimalDigitsInputFilter(digitsAfterZero: Int) :
    InputFilter {

    private val mPattern: Pattern =
        Pattern.compile("[0-9]*((\\.[0-9]{0," + digitsAfterZero + "})?)|(\\.)?")

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val firstFragment = dest.subSequence(0, dstart)
        val changeFragment = source.subSequence(start, end)
        val lastFragment = dest.subSequence(dend, dest.length)

        val result = firstFragment.toString() + changeFragment.toString() + lastFragment.toString()

        val matcher: Matcher = mPattern.matcher(result)
        val matches = matcher.matches()

        return if (!matches) REJECT_CHANGE else ACCEPT_CHANGE
    }

}