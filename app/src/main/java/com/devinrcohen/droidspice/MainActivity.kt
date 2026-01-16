package com.devinrcohen.droidspice

import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.devinrcohen.droidspice.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.*
import com.devinrcohen.droidspice.AnalysisType

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var plotBackCallback: androidx.activity.OnBackPressedCallback

    external fun initNgspice(): String
    external fun runAnalysis(netlist: String, analysisCmd: String): String
    external fun getVecNames(): Array<String>
    external fun takeSamples(): DoubleArray
    external fun getComplexStride(): Int

    private fun norm(s: String) = s.trim().lowercase()
    fun dismissPlot() {
        hidePlotFragment()
    }
    private fun showPlotFragment() {
        android.util.Log.d("PlotHost", "plotHost size = ${binding.plotHost.width}x${binding.plotHost.height}, vis=${binding.plotHost.visibility}")
        binding.plotHost.visibility = View.VISIBLE

        // Enable back interception only while plot is visible
        plotBackCallback.isEnabled = true

        supportFragmentManager.beginTransaction()
            .replace(R.id.plotHost, PlotFragment())
            .addToBackStack("plot")
            .commit()
    }

    // for later, if add close button in plot UI
    private fun hidePlotFragment() {
        // Remove the fragment synchronously so the overlay releases input immediately
        supportFragmentManager.findFragmentById(R.id.plotHost)?.let { frag ->
            supportFragmentManager.beginTransaction()
                .remove(frag)
                .commitNow()
        }

        // Clear any remaining back stack entry named "plot"
        supportFragmentManager.popBackStack("plot", FragmentManager.POP_BACK_STACK_INCLUSIVE)

        binding.plotHost.visibility = View.GONE
        plotBackCallback.isEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        plotBackCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                hidePlotFragment()
            }
        }
        onBackPressedDispatcher.addCallback(this, plotBackCallback)
        // starting values
        // these are unitless since the unit prefix
        // ultimately dictates what goes in the netlist
        val netlist_template : String = getString(R.string.v_divider_netlist)
        var current_netlist : String = ""
        var v1txt : String = "10"
        var r1txt : String = "1.0"
        var r2txt : String = "10.0"
        var c1txt: String = "10.0"

        // max values for initialize progress calculation
        // leave as constants for now, may add UI elements
        // to set different boundaries like falstad, probably
        // unnecessary though

        val v1max : Double = 1000.0
        val r1max : Double = 1000.0
        val r2max : Double = 1000.0
        var c1max : Double = 1000.0

        // progress variables - this will
        // be calculated when initialized
        var v1progress : Int = 0
        var r1progress : Int = 0
        var r2progress : Int = 0
        var c1progress : Int = 0

        var spinnerV1selection : Int = 1
        var spinnerR1selection : Int = 2 // kΩ
        var spinnerR2selection : Int = 4 // GΩ (approx open circuit)
        var spinnerC1selection : Int = 1 // nF

        // one-time: init library
        binding.tvOutput.text = initNgspice()


        fun generateComponentVal(normalized : String, selection : String) : String {
            var suffix : String = selection.toString().dropLast(1)
            if (suffix == "M") suffix = "meg"
            return normalized + suffix
        }

        // TODO("more initialization stuff like diode params")

        fun createNetlist() {
            val vs = generateComponentVal(binding.teV1.text.toString(), binding.spinnerV1.selectedItem.toString())
            val r1 = generateComponentVal(binding.teR1.text.toString(), binding.spinnerR1.selectedItem.toString())
            val r2 = generateComponentVal(binding.teR2.text.toString(), binding.spinnerR2.selectedItem.toString())
            val c1 = generateComponentVal(binding.teC1.text.toString(), binding.spinnerC1.selectedItem.toString())
            current_netlist = netlist_template
                .replace("[vs]", vs, true)
                .replace("[r1]", r1, true)
                .replace("[r2]", r2, true)
                .replace("[l1]", "0", true)
                .replace("[c1]", c1, true)
                .trimIndent()
            //binding.tvNetlist.setText(current_netlist)
        }

        val suffixListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressUiCallbacks) return
                createNetlist()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        fun updateFromUI() {
            // populate netlist and UI based off of default values
            // this function SHOULD NOT use getters for UI elements
            binding.spinnerV1.setSelection(spinnerV1selection) // default volt
            binding.spinnerR1.setSelection(spinnerR1selection) // default kΩ
            binding.spinnerR2.setSelection(spinnerR2selection) // default GΩ
            binding.spinnerC1.setSelection(spinnerC1selection) // default pF

            v1progress = (v1txt.toDouble() / v1max * 100).toInt()
            r1progress = (r1txt.toDouble() / r1max * 100).toInt()
            r2progress = (r2txt.toDouble() / r2max * 100).toInt()
            c1progress = (c1txt.toDouble() / c1max * 100).toInt()

            binding.sbV1.setProgress(v1progress, false)
            binding.sbR1.setProgress(r1progress, false)
            binding.sbR2.setProgress(r2progress, false)
            binding.sbC1.setProgress(c1progress, false)

            binding.teV1.setText(v1txt)
            binding.teR1.setText(r1txt)
            binding.teR2.setText(r2txt)
            binding.teC1.setText(c1txt)
            createNetlist()
        }

        // populate UI controls
        updateFromUI()

        // do simulation
        //binding.tvOutput.text = initNgspice()
        binding.btnRunTran.setOnClickListener {
            var response = runAnalysis(current_netlist, "tran 0.1u 100u")
            val names = getVecNames()
            val stride = getComplexStride() // 1 for real-only, 2 for real+imag
            val data = takeSamples()

            val indexByName = HashMap<String, Int>(names.size)
            for (i in names.indices) {
                indexByName[norm(names[i])] = i
            }

            val vecCount = names.size
            val rowLen = vecCount * stride
            val sampleCount = if (rowLen > 0) data.size / rowLen else 0
            //val mySample = 105
            fun realAt(sample: Int, vecIndex: Int): Double =
                data[sample * rowLen + vecIndex * stride]
            fun imagAt(sample: Int, vecIndex: Int): Double =
                data[sample * rowLen + vecIndex * stride + 1]
            // OP should yield exactly one sample (sampleCount == 1)
            val idxV2 = indexByName[norm("v(2)")]
            val idxTime = indexByName[norm("time")]

            if (sampleCount > 0 && idxV2 != null /*&& idxV4 != null && idxIVs != null && idxIL1 != null*/ && idxTime != null) {
                val timeSec = DoubleArray(sampleCount)
                val y = DoubleArray(sampleCount)

                for (s in 0 until sampleCount){
                    val time = realAt(s, idxTime)
                    val v2 = realAt(s, idxV2)

                    // update vectors to plot
                    timeSec[s] = time
                    y[s] = v2
                    response += String.format(Locale.US, "%.6f", time) + " s, " + String.format(
                        Locale.US,
                        "%.3f",
                        v2
                    ) + " V\n"
                }

                PlotDataHolder.x = timeSec
                PlotDataHolder.y = y
                PlotDataHolder.label = "V(2) (V)"
                PlotDataHolder.type = AnalysisType.TRAN
                showPlotFragment()
            } else {
                response += "\n[WARN] No transient sample data available (names=${names.size}, stride=$stride, data=${data.size})\n"
            }

            binding.tvOutput.text = response
        }

        binding.btnRunAC.setOnClickListener {
            var response = runAnalysis(current_netlist, "ac dec 20 0.1 100meg")
            //var response = runAnalysis(current_netlist, "tran 0.1u 1m")
            val names = getVecNames()
            val stride = getComplexStride() // 1 for real-only, 2 for real+imag
            val data = takeSamples()

            val indexByName = HashMap<String, Int>(names.size)
            for (i in names.indices) {
                indexByName[norm(names[i])] = i
            }

            val vecCount = names.size
            val rowLen = vecCount * stride
            val sampleCount = if (rowLen > 0) data.size / rowLen else 0
            //val mySample = 105
            fun realAt(sample: Int, vecIndex: Int): Double =
                data[sample * rowLen + vecIndex * stride]
            fun imagAt(sample: Int, vecIndex: Int): Double =
                data[sample * rowLen + vecIndex * stride + 1]
            // OP should yield exactly one sample (sampleCount == 1)
            val idxV2 = indexByName[norm("v(2)")]
            val idxFreq = indexByName[norm("frequency")]

            if (sampleCount > 0 && idxV2 != null /*&& idxV4 != null && idxIVs != null && idxIL1 != null*/ && idxFreq != null) {
                val freqHz = DoubleArray(sampleCount)
                val yDb = DoubleArray(sampleCount)

                for (s in 0 until sampleCount){
                    val freq = realAt(s, idxFreq)
                    val v2real = realAt(s, idxV2)
                    val v2imag = imagAt(s, idxV2)
                    val v2power = v2real * v2real + v2imag * v2imag
                    val v2dB = if(v2power > 0.0) 10 * log10(v2power) else Double.NEGATIVE_INFINITY
                    val v2phase = atan2(v2imag, v2real) * 180.0 / PI

                    // update vectors to plot
                    freqHz[s] = freq
                    yDb[s] = v2dB
                    response += String.format(Locale.US, "%.1f", freq) + " Hz, " + String.format(
                        Locale.US,
                        "%.3f",
                        v2dB
                    ) + " dB, " + String.format(Locale.US, "%.1f", v2phase) + "°\n"
                }

                PlotDataHolder.x = freqHz
                PlotDataHolder.y = yDb
                PlotDataHolder.label = "V(2) magnitude (dB)"
                PlotDataHolder.type = AnalysisType.FREQ
                showPlotFragment()
            } else {
                response += "\n[WARN] No AC sample data available (names=${names.size}, stride=$stride, data=${data.size})\n"
            }

            binding.tvOutput.text = response
        }

        binding.btnRunOP.setOnClickListener {
            var response = runAnalysis(current_netlist, "op")

            val names = getVecNames()
            val stride = getComplexStride() // 1 for real-only, 2 for real+imag
            val data = takeSamples()

            val indexByName = HashMap<String, Int>(names.size)
            for (i in names.indices) {
                indexByName[norm(names[i])] = i
            }

            val vecCount = names.size
            val rowLen = vecCount * stride
            val sampleCount = if (rowLen > 0) data.size / rowLen else 0

            fun realAt(sample: Int, vecIndex: Int): Double =
                data[sample * rowLen + vecIndex * stride]

            // OP should yield exactly one sample (sampleCount == 1)
            val idxV2 = indexByName[norm("v(2)")]
            val idxV4 = indexByName[norm("v(4)")]
            val idxIVs = indexByName[norm("vs#branch")]
            val idxIL1 = indexByName[norm("l1#branch")]

            if (sampleCount > 0 && idxV2 != null && idxV4 != null && idxIVs != null && idxIL1 != null) {
                val mySample : Int = 0
                val v2 = realAt(mySample, idxV2)
                val v4 = realAt(mySample, idxV4)
                val iVs_mA = realAt(mySample, idxIVs) * 1000.0
                val iL1_mA = realAt(mySample, idxIL1) * 1000.0

                response += "V(4) = " + String.format(Locale.US, "%.1f", v4) + " V\n"
                response += "V(2) = " + String.format(Locale.US, "%.3f", v2) + " V\n"
                response += "I(Vs) = " + String.format(Locale.US, "%.2f", iVs_mA) + " mA\n"
                response += "I(L1) = " + String.format(Locale.US, "%.2f", iL1_mA) + " mA\n"
            } else {
                response += "\n[WARN] No OP sample data available (names=${names.size}, stride=$stride, data=${data.size})\n"
            }

            binding.tvOutput.text = response
        }
        // SEEKBAR LISTENERS
        binding.sbV1.setOnSeekBarChangeListener (object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v1val : Double = v1max * progress / 100.0
                // eliminate feedback loops with textedit changes
                withSuppressedCallbacks {
                    binding.teV1.setText(v1val.toString())
                }
                createNetlist()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        binding.sbR1.setOnSeekBarChangeListener (object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val r1val : Double = r1max * progress / 100.0

                withSuppressedCallbacks{
                    binding.teR1.setText(r1val.toString())
                }
                createNetlist()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        binding.sbR2.setOnSeekBarChangeListener (object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val r2val : Double = r2max * progress / 100.0
                withSuppressedCallbacks {
                    binding.teR2.setText(r2val.toString())
                }
                createNetlist()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        binding.sbC1.setOnSeekBarChangeListener (object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val c1val : Double = c1max * progress / 100.0
                withSuppressedCallbacks {
                    binding.teC1.setText(c1val.toString())
                }
                createNetlist()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        // TEXTEDIT LISTENERS
        binding.teV1.doAfterTextChanged {
            if (suppressUiCallbacks) return@doAfterTextChanged
            createNetlist()
        }

        binding.teR1.doAfterTextChanged {
            if (suppressUiCallbacks) return@doAfterTextChanged
            createNetlist()
        }

        binding.teR2.doAfterTextChanged {
            if (suppressUiCallbacks) return@doAfterTextChanged
            createNetlist()
        }

        binding.teC1.doAfterTextChanged {
            if (suppressUiCallbacks) return@doAfterTextChanged
            createNetlist()
        }

        // SPINNER LISTENERS
        binding.spinnerV1.onItemSelectedListener = suffixListener
        binding.spinnerR1.onItemSelectedListener = suffixListener
        binding.spinnerR2.onItemSelectedListener = suffixListener
        binding.spinnerC1.onItemSelectedListener = suffixListener
    }

    private var suppressUiCallbacks = false
    private fun withSuppressedCallbacks(block: () -> Unit)
    {
        suppressUiCallbacks = true
        try { block() } finally { suppressUiCallbacks = false }
    }

    companion object {
        init {
            System.loadLibrary("ngspice")
            System.loadLibrary("droidspice")
        }
    }
}
