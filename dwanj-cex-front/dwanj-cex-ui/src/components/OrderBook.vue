<template>
  <div class="order-book-container">
    <h2>Order Book ({{ symbol }})</h2>
    <div v-if="!state.isConnected" class="status-disconnected">
      Connecting...
    </div>
    <div v-if="state.error" class="status-error">
      {{ state.error }}
    </div>
    <div class="order-book" v-if="state.isConnected">
      <div class="asks">
        <h3>Asks (卖盘)</h3>
        <table>
          <thead>
            <tr>
              <th>Price (USDT)</th>
              <th>Quantity (BTC)</th>
            </tr>
          </thead>
          <tbody>
            <!-- 卖盘价格从下往上递增，所以渲染时需要反转数组 -->
            <tr v-for="(ask, index) in reversedAsks" :key="index" class="ask-row">
              <td>{{ formatPrice(ask.price) }}</td>
              <td>{{ formatQuantity(ask.quantity) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="spread">
        <!-- 这里可以显示中间价或价差 -->
      </div>

      <div class="bids">
        <h3>Bids (买盘)</h3>
        <table>
          <thead>
            <tr>
              <th>Price (USDT)</th>
              <th>Quantity (BTC)</th>
            </tr>
          </thead>
          <tbody>
            <!-- 买盘价格从上往下递减，直接渲染即可 -->
            <tr v-for="(bid, index) in state.bids" :key="index" class="bid-row">
              <td>{{ formatPrice(bid.price) }}</td>
              <td>{{ formatQuantity(bid.quantity) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script>
import { computed } from 'vue';
import { orderBookState } from '../services/WebSocketService';

export default {
  name: 'OrderBook',
  props: {
    symbol: {
      type: String,
      required: true,
    },
  },
  setup() {
    // 创建一个计算属性来反转卖盘数组，以实现从高到低显示
    const reversedAsks = computed(() => {
      // 创建一个副本再反转，避免修改原始数据
      return [...orderBookState.asks].reverse();
    });

    const formatPrice = (price) => parseFloat(price).toFixed(2);
    const formatQuantity = (quantity) => parseFloat(quantity).toFixed(6);

    return {
      state: orderBookState,
      reversedAsks,
      formatPrice,
      formatQuantity,
    };
  },
};
</script>

<style scoped>
.order-book-container {
  width: 400px; /* 减小宽度以适应并排布局 */
  margin: 20px;
}
.order-book {
  display: flex;
  flex-direction: column; /* 垂直排列 */
}
table {
  width: 100%;
  border-collapse: collapse;
}
th, td {
  border: 1px solid #ddd;
  padding: 4px;
  font-size: 12px;
  text-align: right;
}
th {
  background-color: #f2f2f2;
}
.asks {
  margin-bottom: 10px; /* 卖盘和买盘之间的间距 */
}
.ask-row td:first-child {
  color: #e15241;
}
.bid-row td:first-child {
  color: #26a69a;
}
.status-disconnected {
  color: #f0ad4e;
}
.status-error {
  color: #d9534f;
}
</style>
