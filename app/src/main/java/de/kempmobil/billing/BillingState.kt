package de.kempmobil.billing

enum class BillingState(val value: Int) {

    AD_VERSION(0),
    FULL_VERSION(1);


    companion object {

        fun from(id: Int): BillingState {
            values().forEach { appState ->
                if (appState.value == id) {
                    return appState
                }
            }
            return AD_VERSION
        }
    }
}