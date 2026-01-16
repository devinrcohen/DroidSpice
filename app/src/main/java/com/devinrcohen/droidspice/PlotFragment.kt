package com.devinrcohen.droidspice

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import android.widget.TextView
import android.widget.Button
import com.devinrcohen.droidspice.AnalysisType // probably not necessary
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

        if (PlotDataHolder.type == AnalysisType.FREQ) {
            val f = PlotDataHolder.x
            val y = PlotDataHolder.y
            if (f != null && y != null) {
                plotFrequencyResponse(chart, f, y, PlotDataHolder.label)
            }
        } else if (PlotDataHolder.type == AnalysisType.TRAN) {
            val t = PlotDataHolder.x
            val y = PlotDataHolder.y
            if (t != null && y != null) {
                plotTransientResponse(chart, t, y, PlotDataHolder.label)
            }
        }
    }
}
