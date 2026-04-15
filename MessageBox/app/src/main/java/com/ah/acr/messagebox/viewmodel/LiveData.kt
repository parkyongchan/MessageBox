package com.ah.acr.messagebox.viewmodel

import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.observe

fun <T> MutableLiveData<T>.onNext(next: T) {
    this.value = next
}

fun <T : Any> LiveData<T>.requireValue(): T = checkNotNull(value)

/**
 *  lateinit var viewModel: MyViewModel
 *
 *  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *      super.onViewCreated(view, savedInstanceState)
 *      observe(viewModel.state, ::renderState)
 *  }
 * ```
 */
inline fun <reified T, LD : LiveData<T>> Fragment.Observe(liveData: LD, crossinline block: (T) -> Unit) {
    liveData.observe(viewLifecycleOwner) { block(it) }
}