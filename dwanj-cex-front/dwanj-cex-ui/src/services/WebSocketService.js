import { reactive } from 'vue';
import { useToast } from './useToast';

export const orderBookState = reactive({
  bids: [],
  asks: [],
  isConnected: false,
  error: null,
});

export const tickerState = reactive({
  price: 0,
  lastPrice: 0,
});

const WEBSOCKET_URL = 'ws://localhost:8080/ws/v1';
const { showToast } = useToast();

let socket = null;
let currentSubscriptions = new Set();

const handleSocketMessage = (event) => {
  const message = JSON.parse(event.data);
  console.log(">>> 前端收到WebSocket消息:", message); // 【调试日志】

  if (message.topic) {
    if (message.topic.startsWith('orderbook:')) {
      const data = message.data;
      orderBookState.bids = data.bids || [];
      orderBookState.asks = data.asks || [];
    } else if (message.topic.startsWith('ticker:')) {
      const newPrice = parseFloat(message.data);
      if (!isNaN(newPrice)) {
        tickerState.lastPrice = tickerState.price;
        tickerState.price = newPrice;
      }
    } else if (message.topic.startsWith('private:')) {
      const notification = message.data;
      const toastMessage = `Order ${notification.orderId}: ${notification.status} - ${notification.reason}`;
      showToast(toastMessage, { type: 'info', duration: 5000 });
    }
  } else if (message.event === 'auth') {
    console.log('WebSocket authentication status:', message.status);
  }
};

export function connectWebSocket(symbol) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    // 如果已连接，直接切换订阅
    switchSubscription(symbol);
    return;
  }
  
  if (socket) {
    socket.onopen = null;
    socket.onmessage = null;
    socket.onclose = null;
    socket.onerror = null;
    socket.close();
  }

  socket = new WebSocket(WEBSOCKET_URL);

  socket.onopen = () => {
    orderBookState.isConnected = true;
    orderBookState.error = null;
    // 连接成功后，订阅指定的symbol
    switchSubscription(symbol);
  };

  socket.onmessage = handleSocketMessage;

  socket.onclose = () => {
    orderBookState.isConnected = false;
    socket = null;
    currentSubscriptions.clear();
  };

  socket.onerror = (error) => {
    console.error('WebSocket error:', error);
    orderBookState.error = 'WebSocket connection failed.';
    orderBookState.isConnected = false;
  };
}

function switchSubscription(newSymbol) {
  if (!socket || socket.readyState !== WebSocket.OPEN) {
    return;
  }

  // 1. 取消旧的订阅
  if (currentSubscriptions.size > 0) {
    const oldArgs = Array.from(currentSubscriptions);
    socket.send(JSON.stringify({ op: 'unsubscribe', args: oldArgs }));
    console.log(">>> 发送取消订阅:", oldArgs);
  }

  // 2. 清空并设置新的订阅
  currentSubscriptions.clear();
  currentSubscriptions.add(`orderbook:${newSymbol}`);
  currentSubscriptions.add(`ticker:${newSymbol}`);

  // 3. 发送新的订阅请求
  const newArgs = Array.from(currentSubscriptions);
  socket.send(JSON.stringify({ op: 'subscribe', args: newArgs }));
  console.log(">>> 发送新的订阅:", newArgs);
}

export function authenticate(token) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ op: 'auth', args: [token] }));
  }
}

export function disconnectWebSocket() {
  if (socket) {
    socket.close();
  }
}
