package com.devinrcohen.droidspice

import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun plotFrequencyResponse(
    chart: LineChart,
    freqHz: DoubleArray,
    y: DoubleArray,
    label: String
) {
    require(freqHz.size == y.size)

    // Use log10(f) on x for Bode-style frequency axis
    val entries = ArrayList<Entry>(freqHz.size)
    for (i in freqHz.indices) {
        val f = freqHz[i]
        if (f > 0.0) {
            entries.add(Entry(log10(f).toFloat(), y[i].toFloat()))
        }
    }

    val dataSet = LineDataSet(entries, label).apply {
        setDrawCircles(false)
        setDrawValues(false)
        lineWidth = 1.5f
        mode = LineDataSet.Mode.LINEAR
    }

    chart.data = LineData(dataSet)
    chart.setBackgroundColor(Color.TRANSPARENT)
    chart.description.isEnabled = false

    chart.legend.apply {
        isEnabled = true
        textSize = 12f
        textColor = Color.WHITE
    }

    // X-Axis
    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        setDrawAxisLine(true)
        setDrawGridLines(true)
        setDrawLabels(true)
        textSize = 12f
        textColor = Color.WHITE
        axisLineColor = Color.WHITE
        gridColor = Color.argb(80, 255, 255, 255)

        granularity = 1f
        labelCount = 6

        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val hz = 10.0.pow(value.toDouble())
                return when {
                    hz >= 1e9 -> "${(hz / 1e9).toInt()}GHz"
                    hz >= 1e6 -> "${(hz / 1e6).toInt()}MHz"
                    hz >= 1e3 -> "${(hz / 1e3).toInt()}kHz"
                    else -> hz.toInt().toString() + "Hz"
                }
            }
        }
    }


    // Y axis
    chart.axisRight.isEnabled = false
    chart.axisLeft.apply {
        setDrawAxisLine(true)
        setDrawGridLines(true)
        setDrawLabels(true)
        textSize = 12f
        textColor = Color.WHITE
        axisLineColor = Color.WHITE
        gridColor = Color.argb(80, 255, 255, 255)
        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
    }

    // Make labels definitely visible
    chart.setExtraOffsets(12f, 12f, 16f, 16f)
    chart.invalidate()
}

fun plotTransientResponse(
    chart: LineChart,
    timeSec: DoubleArray,
    y: DoubleArray,
    label: String
) {
    require(timeSec.size == y.size)

    // Use log10(f) on x for Bode-style frequency axis
    val entries = ArrayList<Entry>(timeSec.size)
    for (i in timeSec.indices) {
        val t = timeSec[i]
        if (t >= 0.0) {
            entries.add(Entry(t.toFloat(), y[i].toFloat()))
        }
    }

    val dataSet = LineDataSet(entries, label).apply {
        setDrawCircles(false)
        setDrawValues(false)
        lineWidth = 1.5f
        mode = LineDataSet.Mode.LINEAR
    }

    chart.data = LineData(dataSet)
    chart.setBackgroundColor(Color.TRANSPARENT)
    chart.description.isEnabled = false

    chart.legend.apply {
        isEnabled = true
        textSize = 12f
        textColor = Color.WHITE
    }

    // X-Axis
    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        setDrawAxisLine(true)
        setDrawGridLines(true)
        setDrawLabels(true)
        textSize = 12f
        textColor = Color.WHITE
        axisLineColor = Color.WHITE
        gridColor = Color.argb(80, 255, 255, 255)

        //granularity = 1e-4f
        //labelCount = 5

        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val v = value.toDouble()
                return when {
                    v >= 1.0      -> String.format(Locale.US, "%.2f s", v)
                    v >= 1e-3     -> String.format(Locale.US, "%.2f ms", v * 1e3)
                    v >= 1e-6     -> String.format(Locale.US, "%.2f µs", v * 1e6)
                    v >= 1e-9     -> String.format(Locale.US, "%.2f ns", v * 1e9)
                    v >= 1e-12    -> String.format(Locale.US, "%.2f ps", v * 1e12)
                    v <= 1e-18    ->   "0"
                    else          -> String.format(Locale.US, "%.2e s", v)
                }
            }
        }
    }


    // Y axis
    chart.axisRight.isEnabled = false
    chart.axisLeft.apply {
        setDrawAxisLine(true)
        setDrawGridLines(true)
        setDrawLabels(true)
        textSize = 12f
        textColor = Color.WHITE
        axisLineColor = Color.WHITE
        gridColor = Color.argb(80, 255, 255, 255)
        setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART)
    }

    // Make labels definitely visible
    chart.setExtraOffsets(12f, 12f, 16f, 16f)
    chart.invalidate()
}