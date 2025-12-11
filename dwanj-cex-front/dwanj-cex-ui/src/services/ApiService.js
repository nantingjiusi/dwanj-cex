const API_BASE_URL = 'http://localhost:8080';

let jwtToken = null;
const TOKEN_KEY = 'cex_jwt_token';

function loadTokenFromStorage() {
  const storedToken = localStorage.getItem(TOKEN_KEY);
  if (storedToken) {
    jwtToken = storedToken;
    console.log('Token restored from localStorage.');
  }
}

loadTokenFromStorage();

export function setAuthToken(token) {
  jwtToken = token;
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export function getAuthToken() {
  return jwtToken;
}

export function logout() {
  setAuthToken(null);
  // 可以在这里添加其他清理逻辑，例如清除用户ID等
  console.log('User logged out, token cleared.');
}

async function fetchWithAuth(url, options = {}) {
  if (!jwtToken) {
    throw new Error('Not authenticated. Please login first.');
  }
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${jwtToken}`,
    ...options.headers,
  };
  const response = await fetch(url, { ...options, headers });
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({ message: 'An unknown error occurred' }));
    if (response.status === 401) {
      logout(); // 如果是认证失败，则自动登出
    }
    throw new Error(errorData.message || 'API request failed');
  }
  return response.json();
}

export async function login(username, password) {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) throw new Error('Login failed');
  const data = await response.json();
  if (data.code === 200 && data.data.token) {
    setAuthToken(data.data.token);
    return data.data;
  } else {
    throw new Error(data.message || 'Login failed');
  }
}

export async function placeOrder(order) {
  const data = await fetchWithAuth(`${API_BASE_URL}/api/order/place`, {
    method: 'POST',
    body: JSON.stringify(order),
  });
  if (data.code === 200) return data;
  throw new Error(data.message);
}

export async function getMyOrders() {
  const data = await fetchWithAuth(`${API_BASE_URL}/api/order/my-orders`);
  if (data.code === 200) return data.data;
  throw new Error(data.message);
}

export async function cancelOrder(orderId) {
  const data = await fetchWithAuth(`${API_BASE_URL}/api/order/cancel/${orderId}`, {
    method: 'POST',
  });
  if (data.code === 200) return data;
  throw new Error(data.message);
}

export async function getBalances() {
  const data = await fetchWithAuth(`${API_BASE_URL}/api/wallet/balances`);
  if (data.code === 200) {
    return data.data;
  } else {
    throw new Error(data.message || 'Failed to fetch balances');
  }
}
