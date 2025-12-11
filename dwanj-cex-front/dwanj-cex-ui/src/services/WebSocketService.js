import { reactive } from 'vue';

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
const TICKER_PRICE_KEY = 'cex_last_price'; // localStorage key

// 初始化时从localStorage恢复价格
const storedPrice = localStorage.getItem(TICKER_PRICE_KEY);
if (storedPrice) {
  const price = parseFloat(storedPrice);
  if (!isNaN(price)) {
    tickerState.price = price;
    tickerState.lastPrice = price; // 初始化时lastPrice等于price，避免显示涨跌颜色
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
        // 【关键修复】将最新价格存入localStorage
        localStorage.setItem(TICKER_PRICE_KEY, newPrice.toString());
      }
    } else if (message.event === 'auth') {
      console.log('WebSocket authentication status:', message.status);
    } else if (message.topic && message.topic.startsWith('private:')) {
      console.log('Received private message:', message);
      alert(`Order Update: \nStatus: ${message.data.status}\nReason: ${message.data.reason}\nOrder ID: ${message.data.orderId}`);
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
