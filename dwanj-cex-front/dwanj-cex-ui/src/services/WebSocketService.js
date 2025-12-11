import { reactive } from 'vue';
import { useToast } from './useToast'; // 【新增】导入useToast

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
const TICKER_PRICE_KEY = 'cex_last_price';

const { showToast } = useToast(); // 【新增】获取showToast函数

const storedPrice = localStorage.getItem(TICKER_PRICE_KEY);
if (storedPrice) {
  const price = parseFloat(storedPrice);
  if (!isNaN(price)) {
    tickerState.price = price;
    tickerState.lastPrice = price;
  }
}

let socket = null;

export function connectWebSocket(symbol) {
  if (socket && socket.readyState === WebSocket.OPEN) return;

  socket = new WebSocket(WEBSOCKET_URL);

  socket.onopen = () => {
    orderBookState.isConnected = true;
    orderBookState.error = null;
    subscribeToOrderBook(symbol);
    subscribeToTicker(symbol);
  };

  socket.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.topic === `orderbook:${symbol}`) {
      const data = message.data;
      orderBookState.bids = data.bids || [];
      orderBookState.asks = data.asks || [];
    } else if (message.topic === `ticker:${symbol}`) {
      const newPrice = parseFloat(message.data);
      if (!isNaN(newPrice)) {
        tickerState.lastPrice = tickerState.price;
        tickerState.price = newPrice;
        localStorage.setItem(TICKER_PRICE_KEY, newPrice.toString());
      }
    } else if (message.event === 'auth') {
      console.log('WebSocket authentication status:', message.status);
    } else if (message.topic && message.topic.startsWith('private:')) {
      // 【修改】使用Toast替代alert
      const notification = message.data;
      const toastMessage = `Order ${notification.orderId}: ${notification.status} - ${notification.reason}`;
      showToast(toastMessage, { type: 'info', duration: 5000 });
    }
  };

  socket.onclose = () => {
    orderBookState.isConnected = false;
    socket = null;
  };

  socket.onerror = (error) => {
    console.error('WebSocket error:', error);
    orderBookState.error = 'WebSocket connection failed.';
    orderBookState.isConnected = false;
  };
}

function subscribeToOrderBook(symbol) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ op: 'subscribe', args: [`orderbook:${symbol}`] }));
  }
}

function subscribeToTicker(symbol) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ op: 'subscribe', args: [`ticker:${symbol}`] }));
  }
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
