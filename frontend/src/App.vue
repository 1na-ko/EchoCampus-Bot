<template>
  <a-config-provider
    :theme="{
      token: {
        colorPrimary: '#6366f1',
        borderRadius: 8,
        wireframe: false,
        fontSize: 14,
        fontFamily: `'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif`,
      },
    }"
  >
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
// App configuration
</script>

<style>
/* Global style overrides if necessary, though most are in style.css */
#app {
  height: 100%;
  width: 100%;
}
</style>
