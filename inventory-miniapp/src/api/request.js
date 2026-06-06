// 修改此处 BASE_URL 为实际后端地址（开发用局域网IP，生产用域名）
// const BASE_URL = 'https://hzsf.nengchengcloud.com/api/v1'
const BASE_URL = 'http://192.168.1.229:8888/api/v1'

function getToken() {
  return uni.getStorageSync('token')
}

function handleAuthError() {
  uni.removeStorageSync('token')
  uni.reLaunch({ url: '/pages/login/login' })
}

function request(method, url, data, params) {
  return new Promise((resolve, reject) => {
    let queryString = ''
    if (params) {
      const parts = []
      for (const key in params) {
        if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
          parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(params[key]))
        }
      }
      if (parts.length) queryString = '?' + parts.join('&')
    }

    const token = getToken()
    const header = { 'Content-Type': 'application/json' }
    if (token) header['Authorization'] = 'Bearer ' + token

    uni.request({
      url: BASE_URL + url + queryString,
      method: method,
      header: header,
      data: data,
      timeout: 600000,
      success: (res) => {
        const result = res.data
        if (result.code === 401) {
          handleAuthError()
          reject(new Error(result.message || '未登录'))
          return
        }
        if (result.code === 403) {
          uni.showToast({ title: '权限不足', icon: 'none' })
          reject(new Error(result.message || '权限不足'))
          return
        }
        if (result.code !== 200) {
          uni.showToast({ title: result.message || '请求失败', icon: 'none' })
          reject(new Error(result.message))
          return
        }
        resolve(result)
      },
      fail: (err) => {
        uni.showToast({ title: err.errMsg || '网络错误', icon: 'none' })
        reject(new Error(err.errMsg))
      },
    })
  })
}

export default {
  get(url, config) {
    return request('GET', url, null, config?.params)
  },
  post(url, data) {
    return request('POST', url, data)
  },
  put(url, data) {
    return request('PUT', url, data)
  },
  del(url) {
    return request('DELETE', url)
  },
}
