import request from '@/utils/request'

// 查询校园知识库文档列表
export function listKnowledge(query) {
  return request({
    url: '/system/knowledge/list',
    method: 'get',
    params: query
  })
}

// 查询校园知识库文档详细
export function getKnowledge(docId) {
  return request({
    url: '/system/knowledge/' + docId,
    method: 'get'
  })
}

// 新增校园知识库文档
export function addKnowledge(data) {
  return request({
    url: '/system/knowledge',
    method: 'post',
    data: data
  })
}

// 修改校园知识库文档
export function updateKnowledge(data) {
  return request({
    url: '/system/knowledge',
    method: 'put',
    data: data
  })
}

// 删除校园知识库文档
export function delKnowledge(docId) {
  return request({
    url: '/system/knowledge/' + docId,
    method: 'delete'
  })
}

// 上传知识文档并自动向量化
export function importKnowledgeFile(data) {
  return request({
    url: '/system/knowledge/import-file',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
