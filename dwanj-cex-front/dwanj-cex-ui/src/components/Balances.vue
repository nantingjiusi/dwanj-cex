<template>
  <div class="balances-container">
    <h3>My Assets</h3>
    <button @click="fetchBalances" :disabled="isLoading">
      {{ isLoading ? 'Refreshing...' : 'Refresh' }}
    </button>
    <div v-if="error" class="error-message">{{ error }}</div>
    <table>
      <thead>
        <tr>
          <th>Asset</th>
          <th>Available</th>
          <th>Frozen</th>
          <th>Total</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="balances.length === 0">
          <td colspan="4">No assets yet.</td>
        </tr>
        <tr v-for="balance in balances" :key="balance.asset">
          <td>{{ balance.asset }}</td>
          <td>{{ formatAmount(balance.available) }}</td>
          <td>{{ formatAmount(balance.frozen) }}</td>
          <td>{{ formatAmount(getTotal(balance)) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue';
import { getBalances } from '../services/ApiService';

export default {
  name: 'UserBalances',
  setup() {
    const balances = ref([]);
    const isLoading = ref(false);
    const error = ref(null);

    const fetchBalances = async () => {
      isLoading.value = true;
      error.value = null;
      try {
        balances.value = await getBalances();
      } catch (e) {
        error.value = e.message;
      } finally {
        isLoading.value = false;
      }
    };

    const formatAmount = (amount) => {
      return parseFloat(amount).toFixed(8);
    };

    const getTotal = (balance) => {
      return parseFloat(balance.available) + parseFloat(balance.frozen);
    };

    // 组件挂载时自动加载一次
    onMounted(fetchBalances);

    return {
      balances,
      isLoading,
      error,
      fetchBalances,
      formatAmount,
      getTotal,
    };
  },
};
</script>

<style scoped>
.balances-container {
  width: 400px;
  padding: 20px;
  margin: 20px;
  border: 1px solid #ccc;
  border-radius: 8px;
}
table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 15px;
}
th, td {
  border: 1px solid #ddd;
  padding: 8px;
  font-size: 14px;
  text-align: right;
}
th {
  background-color: #f2f2f2;
  text-align: left;
}
td:first-child {
  text-align: left;
  font-weight: bold;
}
button {
  padding: 8px 12px;
  cursor: pointer;
}
.error-message {
  color: #e15241;
  font-size: 12px;
  margin-top: 10px;
}
</style>
