<template>
  <div class="place-order-form">
    <h2>Place Order</h2>
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
      <form @submit.prevent="handleSubmit">
        <div class="form-group">
          <label>Side:</label>
          <select v-model="order.side">
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
        </div>
        <div class="form-group">
          <label>Price:</label>
          <input v-model="order.price" type="number" step="0.01" required />
        </div>
        <div class="form-group">
          <label>Amount:</label>
          <input v-model="order.amount" type="number" step="0.000001" required />
        </div>
        <button type="submit">Place Order</button>
      </form>
      <p v-if="orderStatus" :class="{ 'success-message': isSuccess, 'error-message': !isSuccess }">
        {{ orderStatus }}
      </p>
    </div>
  </div>
</template>

<script>
import { reactive, ref, getCurrentInstance } from 'vue';
import { login, placeOrder } from '../services/ApiService';
import { authenticate } from '../services/WebSocketService';

export default {
  name: 'PlaceOrderForm',
  props: {
    symbol: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    const { emit } = getCurrentInstance();

    const isLoggedIn = ref(false);
    const loginError = ref(null);
    const loginForm = reactive({
      username: 'testuser',
      password: '123',
    });

    const order = reactive({
      symbol: props.symbol,
      side: 'BUY',
      price: '',
      amount: '',
    });

    const orderStatus = ref('');
    const isSuccess = ref(false);

    const handleLogin = async () => {
      try {
        loginError.value = null;
        const loginResponse = await login(loginForm.username, loginForm.password);
        isLoggedIn.value = true;

        // 登录成功后，通过WebSocket发送认证token
        if (loginResponse.token) {
          authenticate(loginResponse.token);
        }

        emit('loggedIn');
      } catch (error) {
        loginError.value = error.message;
      }
    };

    const handleSubmit = async () => {
      try {
        orderStatus.value = 'Placing order...';
        const response = await placeOrder(order);
        if (response.code === 200) {
          isSuccess.value = true;
          orderStatus.value = `Order placed successfully! Order ID: ${response.data.id}`;
          emit('orderPlaced');
        } else {
          isSuccess.value = false;
          orderStatus.value = `Error: ${response.message}`;
        }
      } catch (error) {
        isSuccess.value = false;
        orderStatus.value = `Error: ${error.message}`;
      }
    };

    return {
      isLoggedIn,
      loginForm,
      loginError,
      order,
      orderStatus,
      isSuccess,
      handleLogin,
      handleSubmit,
    };
  },
};
</script>

<style scoped>
/* 样式保持不变 */
.place-order-form {
  width: 300px;
  padding: 20px;
  border: 1px solid #ccc;
  border-radius: 8px;
  margin: 20px;
}
.form-group {
  margin-bottom: 15px;
}
label {
  display: block;
  margin-bottom: 5px;
}
input, select {
  width: 100%;
  padding: 8px;
  box-sizing: border-box;
}
button {
  width: 100%;
  padding: 10px;
  background-color: #2c3e50;
  color: white;
  border: none;
  cursor: pointer;
}
.login-form input {
  margin-bottom: 10px;
}
.error-message {
  color: #e15241;
  font-size: 12px;
  margin-top: 10px;
}
.success-message {
  color: #26a69a;
  font-size: 12px;
  margin-top: 10px;
}
</style>
