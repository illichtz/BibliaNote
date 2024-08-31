package com.a101apps.biblianote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment

class NoteFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_note, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Ensure that the NavHostFragment is set up correctly
        val navHostFragment = childFragmentManager.findFragmentById(R.id.note_nav_host_fragment) as NavHostFragment
        // Set this NavHostFragment as the primary navigation fragment
        childFragmentManager.beginTransaction()
            .setPrimaryNavigationFragment(navHostFragment)
            .commit()
    }
}