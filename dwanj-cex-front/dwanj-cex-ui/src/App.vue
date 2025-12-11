<template>
  <div id="app">
    <h1>CEX Trading Terminal</h1>
    
    <!-- 【新增】交易对选择器 -->
    <div class="symbol-selector">
      <span>Trading Pair:</span>
      <select v-model="currentSymbol" @change="switchSymbol">
        <option value="BTCUSDT">BTC/USDT</option>
        <option value="ETHUSDT">ETH/USDT</option>
        <option value="ZEUUSDT">ZEU/USDT</option>
      </select>
    </div>

    <div class="main-container">
      <div class="left-panel">
        <Ticker :price="tickerState.price" :lastPrice="tickerState.lastPrice" />
        <OrderBook :symbol="currentSymbol" />
      </div>
      <div class="sidebar">
        <PlaceOrderForm 
          :symbol="currentSymbol" 
          @loggedIn="onLoginSuccess" 
          @orderPlaced="onOrderPlaced"
          @loggedOut="onLogout"
        />
        <Balances ref="balancesComponent" />
      </div>
    </div>
    <div class="orders-container">
      <MyOrders ref="myOrdersComponent" @orderCanceled="onOrderCanceled" />
    </div>
  </div>
</template>

<script>
import { onMounted, onUnmounted, ref } from 'vue';
import OrderBook from './components/OrderBook.vue';
import PlaceOrderForm from './components/PlaceOrderForm.vue';
import Balances from './components/Balances.vue';
import MyOrders from './components/MyOrders.vue';
import Ticker from './components/Ticker.vue';
import { connectWebSocket, disconnectWebSocket, tickerState } from './services/WebSocketService';

export default {
  name: 'App',
  components: {
    OrderBook,
    PlaceOrderForm,
    Balances,
    MyOrders,
    Ticker,
  },
  setup() {
    const currentSymbol = ref('BTCUSDT');
    const balancesComponent = ref(null);
    const myOrdersComponent = ref(null);

    const switchSymbol = () => {
      console.log(`Switching to symbol: ${currentSymbol.value}`);
      disconnectWebSocket();
      // 重置状态，避免显示旧数据
      tickerState.price = 0;
      tickerState.lastPrice = 0;
      connectWebSocket(currentSymbol.value);
    };

    onMounted(() => {
      connectWebSocket(currentSymbol.value);
    });

    onUnmounted(() => {
      disconnectWebSocket();
    });

    const refreshAllData = () => {
      if (balancesComponent.value) {
        balancesComponent.value.fetchBalances();
      }
      if (myOrdersComponent.value) {
        myOrdersComponent.value.fetchOrders();
      }
    };

    const clearAllData = () => {
      if (balancesComponent.value) {
        balancesComponent.value.balances = [];
      }
      if (myOrdersComponent.value) {
        myOrdersComponent.value.orders = [];
      }
    };

    const onLoginSuccess = () => {
      refreshAllData();
    };

    const onLogout = () => {
      clearAllData();
    };

    const onOrderPlaced = () => {
      setTimeout(refreshAllData, 1000);
    };

    const onOrderCanceled = () => {
      setTimeout(refreshAllData, 1000);
    };

    return {
      currentSymbol,
      balancesComponent,
      myOrdersComponent,
      tickerState,
      switchSymbol, // 暴露给模板
      onLoginSuccess,
      onLogout,
      onOrderPlaced,
      onOrderCanceled,
    };
  },
};
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  color: #2c3e50;
}
.symbol-selector {
  text-align: center;
  margin-bottom: 20px;
  font-size: 18px;
}
.main-container, .orders-container {
  display: flex;
  justify-content: center;
  align-items: flex-start;
}
.left-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.sidebar {
  display: flex;
  flex-direction: column;
}
h1 {
  text-align: center;
}
</style>
