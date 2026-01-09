package com.devinrcohen.droidspice

import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.devinrcohen.droidspice.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    external fun initNgspice(): String
    external fun runOp(netlist: String): String

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
        var r1txt : String = "7.0"
        var r2txt : String = "3.0"

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
        var spinnerR2selection : Int = 2

        // one-time: init library
        binding.tvOutput.text = initNgspice()


        fun generateComponentVal(normalized : String, selection : String) : String {
            println("selection: " + selection.toString() + "\n")
            var suffix : String = selection.toString().dropLast(1)
            println("suffix: " + suffix + "\n")
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
                .replace("[c1]", "100p", true)
                .trimIndent()
            binding.tvNetlist.setText(current_netlist)
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
        binding.tvOutput.text = initNgspice()
        binding.btnRunOP.setOnClickListener {
            //val netlist = binding.tvNetlist.text.toString()
            binding.tvOutput.text = runOp(current_netlist)
        }

//        binding.teV1.addTextChangedListener(object: TextWatcher {
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
//                TODO("Not yet implemented")
//            }
//
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                TODO("Not yet implemented")
//            }
//
//            override fun afterTextChanged(s: Editable?) {
//                binding.tiNetlist.setText(createNetlist())
//            }
//        })

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
