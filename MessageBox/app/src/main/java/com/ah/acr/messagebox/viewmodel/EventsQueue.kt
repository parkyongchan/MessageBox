package com.ah.acr.messagebox.viewmodel

import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import java.util.LinkedList
import java.util.Queue

open class EventsQueue : MutableLiveData<Queue<String>>() {

    @MainThread
    fun offer(event: String) {
        val queue = (value ?: LinkedList()).apply {
            add(event)
        }
        value = queue
    }
}


fun Fragment.observe(eventsQueue: EventsQueue, eventHandler: (String) -> Unit) {
    eventsQueue.observe(viewLifecycleOwner) { queue: Queue<String>? ->
        while (queue != null && queue.isNotEmpty()) {
            eventHandler(queue.remove())
        }
    }
}