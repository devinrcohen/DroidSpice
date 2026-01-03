package com.devinrcohen.droidspice

import android.os.Bundle
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

        binding.tvOutput.text = initNgspice()

        binding.btnRunOP.setOnClickListener {
            val netlist = binding.tiNetlist.text.toString()
            binding.tvOutput.text = runOp(netlist)
        }
    }

    companion object {
        init {
            System.loadLibrary("ngspice")
            System.loadLibrary("droidspice")
        }
    }
}
