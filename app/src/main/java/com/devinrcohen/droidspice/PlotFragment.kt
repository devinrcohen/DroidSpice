package com.devinrcohen.droidspice

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import android.widget.TextView
import android.widget.Button

class PlotFragment : Fragment(R.layout.fragment_plot) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val chart = view.findViewById<LineChart>(R.id.lineChart)
        val title = view.findViewById<TextView>(R.id.tvPlotTitle)
        val close = view.findViewById<Button>(R.id.btnClose)

        title.text = PlotDataHolder.label

        close.setOnClickListener {
            // pops the backstack entry created in MainActivity.showPlotFragment()
            //parentFragmentManager.popBackStack()
            (activity as? MainActivity)?.dismissPlot()
        }

        val f = PlotDataHolder.freqHz
        val y = PlotDataHolder.y
        if (f != null && y != null) {
            plotFrequencyResponse(chart, f, y, PlotDataHolder.label)
        }
    }
}
