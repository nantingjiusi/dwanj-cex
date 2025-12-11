<template>
  <div class="order-book-container">
    <h2>Order Book ({{ symbol }})</h2>
    <div v-if="!state.isConnected">Connecting...</div>
    <div v-if="state.error">{{ state.error }}</div>
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
            <!-- 【修改】只渲染处理后的20条数据 -->
            <tr v-for="(ask, index) in limitedAsks" :key="index">
              <td>{{ formatPrice(ask.price) }}</td>
              <td>{{ formatQuantity(ask.quantity) }}</td>
            </tr>
          </tbody>
        </table>
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
            <!-- 【修改】只渲染处理后的20条数据 -->
            <tr v-for="(bid, index) in limitedBids" :key="index">
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
    // 【修改】创建计算属性，只取前20条数据
    const limitedAsks = computed(() => {
      // 取价格最低的20个卖单，然后反转，以实现UI上价格从高到低显示
      return [...orderBookState.asks].slice(0, 20).reverse();
    });

    const limitedBids = computed(() => {
      // 取价格最高的20个买单
      return orderBookState.bids.slice(0, 20);
    });

    const formatPrice = (price) => parseFloat(price).toFixed(2);
    const formatQuantity = (quantity) => parseFloat(quantity).toFixed(6);

    return {
      state: orderBookState,
      limitedAsks,
      limitedBids,
      formatPrice,
      formatQuantity,
    };
  },
};
</script>

<style scoped>
/* 【修改】移除大部分样式，只保留基本布局 */
.order-book-container {
  width: 400px;
  margin: 20px;
  font-family: monospace; /* 使用等宽字体，更像终端 */
}
.order-book {
  display: flex;
  flex-direction: column;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th, td {
  text-align: right;
  padding: 2px 4px;
}
.asks {
  margin-bottom: 5px;
}
</style>
