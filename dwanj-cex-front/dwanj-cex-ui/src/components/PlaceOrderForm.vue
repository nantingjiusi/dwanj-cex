<template>
  <div class="place-order-form">
    <div class="header">
      <h2>Place Order</h2>
      <button v-if="isLoggedIn" @click="handleLogout" class="logout-btn">Logout</button>
    </div>
    <div v-if="!isLoggedIn">
      <div class="login-form">
        <h3>Login First</h3>
        <input v-model="loginForm.username" placeholder="Username" />
        <input v-model="loginForm.password" type="password" placeholder="Password" />
        <button @click="handleLogin">Login</button>
        <p v-if="loginError" class="error-message">{{ loginError }}</p>
      </div>
    </div>
    <div v-if="isLoggedIn">
      <div class="order-type-selector">
        <button :class="{ active: order.type === 'LIMIT' }" @click="order.type = 'LIMIT'">Limit</button>
        <button :class="{ active: order.type === 'MARKET' }" @click="order.type = 'MARKET'">Market</button>
      </div>
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>Side:</label>
          <select v-model="order.side">
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
        </div>
        <div v-if="order.type === 'LIMIT'" class="form-group">
          <label>Price:</label>
          <input v-model="order.price" type="number" step="0.01" required />
        </div>
        <div class="form-group">
          <label>{{ amountLabel }}</label>
          <input v-model="order.inputValue" type="number" :step="amountStep" required />
        </div>
        <button type="submit">Place {{ order.side }} Order</button>
      </form>
      <p v-if="orderStatus" :class="{ 'success-message': isSuccess, 'error-message': !isSuccess }">
        {{ orderStatus }}
      </p>
    </div>
  </div>
</template>

<script>
import { reactive, ref, getCurrentInstance, computed, onMounted } from 'vue';
import { login, placeOrder, getAuthToken, logout } from '../services/ApiService';
import { authenticate } from '../services/WebSocketService';

export default {
  name: 'PlaceOrderForm',
  props: {
    symbol: {
      type: String,
      required: true,
    },
  },
  setup(props, { emit }) {
    const { emit: vueEmit } = getCurrentInstance();
    const isLoggedIn = ref(false);
    const loginError = ref(null);
    const loginForm = reactive({ username: 'testuser', password: '123' });

    const order = reactive({
      type: 'LIMIT',
      side: 'BUY',
      price: '',
      inputValue: '',
    });

    const orderStatus = ref('');
    const isSuccess = ref(false);

    onMounted(() => {
      const token = getAuthToken();
      if (token) {
        isLoggedIn.value = true;
        authenticate(token);
        vueEmit('loggedIn');
      }
    });

    const amountLabel = computed(() => {
      if (order.type === 'MARKET' && order.side === 'BUY') {
        return 'Total (USDT)';
      }
      return `Amount (${props.symbol.replace('USDT', '')})`;
    });

    const amountStep = computed(() => (order.type === 'MARKET' && order.side === 'BUY' ? '0.01' : '0.000001'));

    const handleLogin = async () => {
      try {
        loginError.value = null;
        const loginResponse = await login(loginForm.username, loginForm.password);
        isLoggedIn.value = true;
        if (loginResponse.token) {
          authenticate(loginResponse.token);
        }
        vueEmit('loggedIn');
      } catch (error) {
        loginError.value = error.message;
      }
    };

    const handleLogout = () => {
      logout();
      isLoggedIn.value = false;
      vueEmit('loggedOut');
    };

    const handleSubmit = async () => {
      console.log('handleSubmit called'); // 【调试日志1】确认方法被调用
      try {
        orderStatus.value = 'Placing order...';
        
        const orderToSend = {
          symbol: props.symbol,
          type: order.type,
          side: order.side,
          price: null,
          amount: null,
          quoteAmount: null,
        };

        if (order.type === 'LIMIT') {
          orderToSend.price = order.price;
          orderToSend.amount = order.inputValue;
        } else { // MARKET
          if (order.side === 'BUY') {
            orderToSend.quoteAmount = order.inputValue;
          } else { // MARKET SELL
            orderToSend.amount = order.inputValue;
          }
        }

        console.log('Order to send:', orderToSend); // 【调试日志2】检查将要发送的数据

        const response = await placeOrder(orderToSend);
        
        console.log('API Response:', response); // 【调试日志3】查看API返回结果

        if (response.code === 200) {
          isSuccess.value = true;
          orderStatus.value = `Order placed successfully! Order ID: ${response.data.id}`;
          vueEmit('orderPlaced');
        } else {
          isSuccess.value = false;
          orderStatus.value = `Error: ${response.message}`;
        }
      } catch (error) {
        isSuccess.value = false;
        orderStatus.value = `Error: ${error.message}`;
        console.error('Error placing order:', error); // 【调试日志4】捕获并打印任何异常
      }
    };

    return {
      isLoggedIn, loginForm, loginError, order, orderStatus, isSuccess,
      amountLabel, amountStep, handleLogin, handleLogout, handleSubmit,
    };
  },
};
</script>

<style scoped>
/* 样式保持不变 */
.header { display: flex; justify-content: space-between; align-items: center; }
.logout-btn { padding: 4px 8px; font-size: 12px; background-color: #f5f5f5; border: 1px solid #ccc; cursor: pointer; }
.place-order-form { width: 300px; padding: 20px; border: 1px solid #ccc; border-radius: 8px; margin: 20px; }
.order-type-selector { display: flex; margin-bottom: 15px; }
.order-type-selector button { flex: 1; padding: 8px; border: 1px solid #ccc; background-color: #f0f0f0; cursor: pointer; }
.order-type-selector button.active { background-color: #2c3e50; color: white; border-color: #2c3e50; }
.form-group { margin-bottom: 15px; }
label { display: block; margin-bottom: 5px; }
input, select { width: 100%; padding: 8px; box-sizing: border-box; }
button[type="submit"] { width: 100%; padding: 10px; background-color: #2c3e50; color: white; border: none; cursor: pointer; }
.login-form input { margin-bottom: 10px; }
.error-message { color: #e15241; font-size: 12px; margin-top: 10px; }
.success-message { color: #26a69a; font-size: 12px; margin-top: 10px; }
</style>
