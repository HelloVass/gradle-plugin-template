package me.yangxiaobin.androidapp.fragmengs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.yangxiaobin.androidapp.R

class KotlinFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        initBeforeView()
        return inflater.inflate(R.layout.fragment_kotlin, container, false)
    }

    private fun initBeforeView() {
        println("----> init kotlin fragment")
    }
}
