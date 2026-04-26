import request from '@/utils/request'

// 查询AI校园热点问答库列表
export function listQa(query) {
  return request({
    url: '/system/qa/list',
    method: 'get',
    params: query
  })
}

// 查询AI校园热点问答库详细
export function getQa(qaId) {
  return request({
    url: '/system/qa/' + qaId,
    method: 'get'
  })
}

// 新增AI校园热点问答库
export function addQa(data) {
  return request({
    url: '/system/qa',
    method: 'post',
    data: data
  })
}

// 修改AI校园热点问答库
export function updateQa(data) {
  return request({
    url: '/system/qa',
    method: 'put',
    data: data
  })
}

// 删除AI校园热点问答库
export function delQa(qaId) {
  return request({
    url: '/system/qa/' + qaId,
    method: 'delete'
  })
}
