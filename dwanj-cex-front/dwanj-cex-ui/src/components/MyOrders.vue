<template>
  <div class="my-orders-container">
    <h3>My Orders</h3>
    <button @click="fetchOrders" :disabled="isLoading">
      {{ isLoading ? 'Refreshing...' : 'Refresh' }}
    </button>
    <div v-if="error" class="error-message">{{ error }}</div>
    <div class="orders-table-wrapper">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Symbol</th>
            <th>Type</th>
            <th>Side</th>
            <th>Price</th>
            <th>Amount</th>
            <th>Filled</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="orders.length === 0">
            <td colspan="9">No orders yet.</td>
          </tr>
          <tr v-for="order in orders" :key="order.id" :class="`status-${order.status.toLowerCase()}`">
            <td>{{ order.id }}</td>
            <td>{{ order.symbol }}</td>
            <td>{{ order.type }}</td>
            <td :class="order.side === 'BUY' ? 'buy-text' : 'sell-text'">{{ order.side }}</td>
            <td>{{ formatAmount(order.price, 2) }}</td>
            <td>{{ formatAmount(order.amount, 6) }}</td>
            <td>{{ formatAmount(order.filled, 6) }}</td>
            <td>{{ formatStatus(order) }}</td>
            <td>
              <button
                v-if="canBeCanceled(order.status)"
                @click="handleCancel(order.id)"
                class="cancel-btn"
                :disabled="cancelingOrderId === order.id"
              >
                {{ cancelingOrderId === order.id ? '...' : 'Cancel' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import { ref } from 'vue';
import { getMyOrders, cancelOrder } from '../services/ApiService';

export default {
  name: 'MyOrders',
  setup(props, { emit }) {
    const orders = ref([]);
    const isLoading = ref(false);
    const error = ref(null);
    const cancelingOrderId = ref(null);

    const fetchOrders = async () => {
      isLoading.value = true;
      error.value = null;
      try {
        orders.value = await getMyOrders();
      } catch (e) {
        error.value = e.message;
      } finally {
        isLoading.value = false;
      }
    };

    const canBeCanceled = (status) => {
      return status === 'NEW' || status === 'PARTIAL';
    };

    const handleCancel = async (orderId) => {
      cancelingOrderId.value = orderId;
      try {
        await cancelOrder(orderId);
        setTimeout(() => {
          fetchOrders();
          emit('orderCanceled');
        }, 1000);
      } catch (e) {
        alert(`Failed to cancel order ${orderId}: ${e.message}`);
      } finally {
        cancelingOrderId.value = null;
      }
    };

    const formatAmount = (amount, precision) => {
      return parseFloat(amount).toFixed(precision);
    };

    const formatStatus = (order) => {
      if (order.status === 'PARTIALLY_FILLED_AND_CLOSED') {
        return `Partial Filled (${formatAmount(order.filled, 6)}) & Closed`;
      }
      return order.status;
    };

    const expose = { fetchOrders };
    emit('expose', expose);

    return {
      orders,
      isLoading,
      error,
      cancelingOrderId,
      fetchOrders,
      canBeCanceled,
      handleCancel,
      formatAmount,
      formatStatus,
    };
  },
};
</script>

<style scoped>
.my-orders-container {
  width: 900px;
  padding: 20px;
  margin: 20px;
  border: 1px solid #ccc;
  border-radius: 8px;
}
.orders-table-wrapper {
  max-height: 400px;
  overflow-y: auto;
  margin-top: 15px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th, td {
  border: 1px solid #ddd;
  padding: 8px;
  font-size: 12px;
  text-align: center;
}
th {
  background-color: #f2f2f2;
  position: sticky;
  top: 0;
}
.buy-text { color: #26a69a; }
.sell-text { color: #e15241; }
.status-filled { background-color: #f0f9eb; }
.status-canceled { background-color: #fef0f0; text-decoration: line-through; }
.status-partially_filled_and_closed { background-color: #fffbe6; color: #8a6d3b; } /* 浅黄色 */
.cancel-btn {
  padding: 2px 6px;
  font-size: 10px;
  cursor: pointer;
  background-color: #fbebeb;
  color: #e15241;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
}
.cancel-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.error-message {
  color: #e15241;
}
</style>
