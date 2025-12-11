<template>
  <transition name="toast-fade">
    <div v-if="visible" :class="['toast', `toast-${type}`]">
      <p>{{ message }}</p>
    </div>
  </transition>
</template>

<script>
import { ref, onMounted } from 'vue';

export default {
  name: 'Toast',
  props: {
    message: {
      type: String,
      required: true,
    },
    type: {
      type: String,
      default: 'info', // info, success, error
    },
    duration: {
      type: Number,
      default: 3000,
    },
  },
  setup(props) {
    const visible = ref(false);

    onMounted(() => {
      visible.value = true;
      setTimeout(() => {
        visible.value = false;
      }, props.duration);
    });

    return {
      visible,
    };
  },
};
</script>

<style scoped>
.toast {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 20px;
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  z-index: 9999;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.toast-info {
  background-color: #2c3e50;
}

.toast-success {
  background-color: #26a69a;
}

.toast-error {
  background-color: #e15241;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.5s, transform 0.5s;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -20px);
}
</style>
