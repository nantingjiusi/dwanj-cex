<template>
  <div class="ticker-container">
    <h3>Last Price</h3>
    <div :class="priceClass" class="price">
      {{ formattedPrice }}
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue';

export default {
  name: 'TickerDisplay',
  props: {
    price: {
      type: Number,
      required: true,
    },
    lastPrice: {
      type: Number,
      required: true,
    },
  },
  setup(props) {
    const formattedPrice = computed(() => {
      return props.price > 0 ? props.price.toFixed(2) : '---';
    });

    const priceClass = computed(() => {
      if (props.price > props.lastPrice) {
        return 'price-up';
      } else if (props.price < props.lastPrice) {
        return 'price-down';
      }
      return 'price-stable';
    });

    return {
      formattedPrice,
      priceClass,
    };
  },
};
</script>

<style scoped>
.ticker-container {
  padding: 10px;
  text-align: center;
}
.price {
  font-size: 24px;
  font-weight: bold;
  transition: color 0.3s;
}
.price-up {
  color: #26a69a; /* 绿色 */
}
.price-down {
  color: #e15241; /* 红色 */
}
.price-stable {
  color: #333;
}
</style>
