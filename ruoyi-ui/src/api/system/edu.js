import request from '@/utils/request'

// 查询学生成绩
export function getStudentScore(params) {
  return request({
    url: '/system/edu/api/score',
    method: 'get',
    params
  })
}

// 查询一卡通余额
export function getCardBalance(params) {
  return request({
    url: '/system/edu/api/card/balance',
    method: 'get',
    params
  })
}
