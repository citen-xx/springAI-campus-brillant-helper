import request from '@/utils/request'

export function getMyScore(params) {
  return request({
    url: '/system/edu/api/score',
    method: 'get',
    params
  })
}

export function getMyCardBalance() {
  return request({
    url: '/system/edu/api/card/balance',
    method: 'get'
  })
}

export function getAdminScore(params) {
  return request({
    url: '/system/admin/edu/score',
    method: 'get',
    params
  })
}

export function getAdminCardBalance(params) {
  return request({
    url: '/system/admin/edu/card/balance',
    method: 'get',
    params
  })
}

export const getStudentScore = getMyScore
export const getCardBalance = getMyCardBalance
