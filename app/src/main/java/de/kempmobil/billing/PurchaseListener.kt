package de.kempmobil.billing

interface PurchaseListener {

    /**
     * Called when purchasing the full version completed successfully. Note that this method is only
     * called when a purchase flow has been initiated by the user. When a pending purchase has been
     * completed, this method is not called. This is only reflected in the change of AppState
     */
    fun purchaseCompleted()
}