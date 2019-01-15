package com.nxsyed.dating_swipe

data class Spot(
        val id: Long = counter++,
        var name: String,
        val distance: String,
        val url: String
) {
    companion object {
        private var counter = 0L
    }
}
