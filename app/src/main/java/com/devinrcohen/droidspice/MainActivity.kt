package com.devinrcohen.droidspice

import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.devinrcohen.droidspice.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding


    external fun initNgspice(): String
    external fun runAnalysis(netlist: String, analysisCmd: String): String
    external fun getVecNames(): Array<String>
    external fun takeSamples(): DoubleArray
    external fun getComplexStride(): Int

    private fun norm(s: String) = s.trim().lowercase()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // starting values
        // these are unitless since the unit prefix
        // ultimately dictates what goes in the netlist
        val netlist_template : String = getString(R.string.v_divider_netlist)
        var current_netlist : String = ""
        var v1txt : String = "10.0"
        var r1txt : String = "1.0"
        var r2txt : String = "10.0"

        // max values for initialize progress calculation
        // leave as constants for now, may add UI elements
        // to set different boundaries like falstad, probably
        // unnecessary though

        val v1max : Double = 1000.0
        val r1max : Double = 1000.0
        val r2max : Double = 1000.0

        // progress variables - this will
        // be calculated when initialized
        var v1progress : Int = 0
        var r1progress : Int = 0
        var r2progress : Int = 0

        var spinnerV1selection : Int = 1
        var spinnerR1selection : Int = 2
        var spinnerR2selection : Int = 4

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

            current_netlist = netlist_template
                .replace("[vs]", vs, true)
                .replace("[r1]", r1, true)
                .replace("[r2]", r2, true)
                .replace("[l1]", "0", true)
                .replace("[c1]", "100p", true)
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
            binding.spinnerR1.setSelection(spinnerR1selection) // default kohm
            binding.spinnerR2.setSelection(spinnerR2selection) // default kohm

            v1progress = (v1txt.toDouble() / v1max * 100).toInt()
            r1progress = (r1txt.toDouble() / r1max * 100).toInt()
            r2progress = (r2txt.toDouble() / r2max * 100).toInt()

            binding.sbV1.setProgress(v1progress, false)
            binding.sbR1.setProgress(r1progress, false)
            binding.sbR2.setProgress(r2progress, false)

            binding.teV1.setText(v1txt)
            binding.teR1.setText(r1txt)
            binding.teR2.setText(r2txt)
            createNetlist()
        }

        // populate UI controls
        updateFromUI()

        // do simulation
        //binding.tvOutput.text = initNgspice()
        binding.btnRunAC.setOnClickListener {
            var response = runAnalysis(current_netlist, "ac dec 20 1 1meg")

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
            val mySample = 105
            fun realAt(sample: Int, vecIndex: Int): Double =
                data[sample * rowLen + vecIndex * stride]
            fun imagAt(sample: Int, vecIndex: Int): Double =
                data[sample * rowLen + vecIndex * stride + 1]
            // OP should yield exactly one sample (sampleCount == 1)
            val idxV2 = indexByName[norm("v(2)")]
            val idxV4 = indexByName[norm("v(4)")]
            val idxIVs = indexByName[norm("vs#branch")]
            val idxIL1 = indexByName[norm("l1#branch")]
            val idxFreq = indexByName[norm("frequency")]

            if (sampleCount > 0 && idxV2 != null && idxV4 != null && idxIVs != null && idxIL1 != null && idxFreq != null) {
                val v2real = realAt(mySample, idxV2)
                val v2imag = imagAt(mySample,idxV2)
                val v4real = realAt(mySample, idxV4)
                val v4imag = imagAt(mySample, idxV4)
//                val iVs_mA = realAt(0, idxIVs) * 1000.0
//                val iL1_mA = realAt(0, idxIL1) * 1000.0
                val v2magdB = 10*log10(v2real*v2real + v2imag*v2imag)
                val v2phase = atan2(v2imag, v2real) * 180.0 / PI
                val v4magdB = 10*log10(v4real*v4real + v4imag*v4imag)
                val v4phase = atan2(v4imag, v4real) * 180.0 / PI
                val freq = realAt(mySample,idxFreq)
                response += "V(4) = " + String.format(Locale.US, "%.1f", v4magdB) + " dB, " + String.format(Locale.US, "%.1f", v4phase)  + "°\n"
                response += "V(2) = " + String.format(Locale.US, "%.3f", v2magdB) + " dB, " + String.format(Locale.US, "%.1f", v4phase)  + "°\n"
//                response += "I(Vs) = " + String.format(Locale.US, "%.2f", iVs_mA) + " mA\n"
//                response += "I(L1) = " + String.format(Locale.US, "%.2f", iL1_mA) + " mA\n"
                response += "freq = " + String.format(Locale.US,"%.1f", freq) + " Hz\n"
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

        // SPINNER LISTENERS
        binding.spinnerV1.onItemSelectedListener = suffixListener
        binding.spinnerR1.onItemSelectedListener = suffixListener
        binding.spinnerR2.onItemSelectedListener = suffixListener
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
