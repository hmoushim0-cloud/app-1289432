package sa.factory.qalamcampussmarted.data.repository

import sa.factory.qalamcampussmarted.domain.repository.QalamCampusRepository
import sa.factory.qalamcampussmarted.hardware.HardwareSensorDriver
import sa.factory.qalamcampussmarted.model.QalamCampusItem
import sa.factory.qalamcampussmarted.model.QalamCampusOrder
import sa.factory.qalamcampussmarted.model.QalamCampusTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class QalamCampusRepositoryImpl(
    private val sensorDriver: HardwareSensorDriver
) : QalamCampusRepository {

    private val _catalog = MutableStateFlow<List<QalamCampusItem>>(listOf(
        QalamCampusItem("1", "تقييم الطلاب based on mid-term and final exam results", "Analyzes student performance based on mid-term and final exam results to identify learning gaps", 750.0, "مفصل", "النتائج الدراسية"),
        QalamCampusItem("2", "تقييم فعالية المناهج", "Evaluates the effectiveness of the curriculum based on student performance", 1200.0, "عالي", "المناهج"),
        QalamCampusItem("3", "تقييم المواد أو المهارات التي تحتاج إلى دعم إضافي", "Identifies subjects or skills that require additional support based on student performance", 950.0, "مبتكر", "المواسم الدراسية")
    ))
    override val catalogFlow: Flow<List<QalamCampusItem>> = _catalog.asStateFlow()

    private val _cart = MutableStateFlow<List<QalamCampusItem>>(emptyList())
    override val activeCart: StateFlow<List<QalamCampusItem>> = _cart.asStateFlow()

    override val telemetryFlow: Flow<QalamCampusTelemetry> = sensorDriver.observePeripheralStream()
        .map { frame ->
            QalamCampusTelemetry(
                peripheralName = frame.peripheralName,
                status = frame.connectionState,
                liveReading = frame.livePayload,
                batteryPct = 96,
                signalDbm = -54
            )
        }

    override fun addToCart(item: QalamCampusItem) {
        val current = _cart.value.toMutableList()
        current.add(item)
        _cart.value = current
    }

    override fun removeFromCart(item: QalamCampusItem) {
        val current = _cart.value.toMutableList()
        current.remove(item)
        _cart.value = current
    }

    override fun clearCart() {
        _cart.value = emptyList()
    }

    override suspend fun checkout(paymentMethod: String): QalamCampusOrder {
        val currentItems = _cart.value.toList()
        val subtotal = currentItems.sumOf { it.priceSar }
        val vat = subtotal * 0.15
        val total = subtotal + vat
        val order = QalamCampusOrder(
            orderId = "ORD-${System.currentTimeMillis() % 100000}",
            items = currentItems,
            subtotalSar = subtotal,
            vatSar = vat,
            totalSar = total,
            paymentMethod = paymentMethod
        )
        clearCart()
        return order
    }

    override suspend fun refreshCatalog() {
        // Offline-first sync logic
    }
}
