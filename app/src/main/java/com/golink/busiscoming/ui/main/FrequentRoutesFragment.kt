package com.golink.busiscoming.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.golink.busiscoming.R
import com.golink.busiscoming.ui.common.ResultListDrivenAppBar
import com.google.android.material.appbar.AppBarLayout

class FrequentRoutesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_frequent_routes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ResultListDrivenAppBar.install(view.findViewById<AppBarLayout>(R.id.frequentAppBar))
        view.findViewById<NestedScrollView>(R.id.frequentEmptyScroll)
            .isNestedScrollingEnabled = false
        (activity as? MainActivity)?.onFrequentRoutesViewReady()
    }
}
