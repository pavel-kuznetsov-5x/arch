package com.example.arch_sample

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import kotlinx.coroutines.FlowPreview

@FlowPreview
class MainFragment: Fragment(R.layout.fragment_main) {

    private val vm: MainViewModel by viewModels {
        Injector.viewModelFactory
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val button = view.findViewById<TextView>(R.id.button)
        val etUsername = view.findViewById<EditText>(R.id.etUsername)

        vm.viewState.observe(viewLifecycleOwner) {
            try {
                displayViewState(view, it)
            } catch (e: Exception) {
                vm.onError(e)
            }
        }

        observe(vm.toastEvent) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }

        addOnClickListener(button) { _, vm ->
            vm.onLogin()
        }

        etUsername.addTextChangedListener {
            vm.onTextChanged(it.toString())
        }
    }

    private fun displayViewState(view: View, viewState: ViewState) {
        val etUsername = view.findViewById<EditText>(R.id.etUsername)
        val tvState = view.findViewById<TextView>(R.id.tvState)
        val button = view.findViewById<TextView>(R.id.button)

        tvState.text = viewState.textState
        etUsername.visibility = if(viewState.loginViewsVisible) View.VISIBLE else View.GONE
        button.visibility = if(viewState.loginViewsVisible) View.VISIBLE else View.GONE
        tvState.text = viewState.textState
    }

    private fun addOnClickListener(view: View, onClick: (View, MainViewModel) -> Unit) {
        view.setOnClickListener {
            withVm()?.let {
                try {
                    onClick.invoke(view, it)
                } catch (e: Exception) {
                    it.onError(e)
                }
            }
        }
    }

    private fun <T> observe(liveData: LiveData<T>, onEvent: (T) -> Unit) {
        liveData.observe(viewLifecycleOwner) {
            try {
                onEvent.invoke(it)
            } catch (e: Exception) {
                vm.onError(e)
            }
        }
    }

    private fun withVm(): MainViewModel? {
        return try {
            vm
        } catch (e: Exception) {
            // if event triggered on detached fragment or any other issue with wm
            // should report error
            null
        }
    }
}
