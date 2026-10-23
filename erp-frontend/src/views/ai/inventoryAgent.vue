<template>
  <div class="assistant-page">
    <div class="heading"><div><h1>库存采购助手</h1><p>集中查看商品库存、销量和采购资料，是否补货及采购数量由采购人员判断。</p></div></div>
    <el-card shadow="never" class="section">
      <template #header><div class="overview-header"><strong>采购数据总览</strong><el-button :loading="overviewLoading" @click="loadOverview(overviewPage)">刷新</el-button></div></template>
      <p v-if="overview" class="muted">{{ overview.scope }} · 共 {{ overview.total }} 个商品 SKU · {{ overview.limitation }}</p>
      <el-alert v-if="overview?.emptyScopesTruncated" title="本页缺货范围超过 1000 条，明细仅显示前 1000 条；请缩小账号范围或分页核查。" type="warning" :closable="false" show-icon />
      <el-alert v-if="overviewError" title="库存扫描加载失败，请检查后端服务和账号权限后重试。" type="error" :closable="false" show-icon />
      <el-table v-if="overview" ref="overviewTable" v-loading="overviewLoading" :data="overview.records" row-key="skuId" @expand-change="expandDossier" empty-text="当前范围没有商品">
        <el-table-column type="expand" width="60"><template #header>资料</template><template #default="scope">
          <div class="dossier" v-loading="dossierLoading[scope.row.skuId]">
            <div class="overview-header"><strong>{{ scope.row.goodsName }} · 采购资料</strong><el-button :loading="dossierLoading[scope.row.skuId]" @click="loadDossier(scope.row.skuId)">刷新资料</el-button></div>
            <el-alert v-if="dossierErrors[scope.row.skuId]" title="资料查询失败，请重试。未查询到的数据不能视作零。" type="error" :closable="false" />
            <template v-if="dossiers[scope.row.skuId]">
              <p class="muted">查询时间 {{ displayTime(dossiers[scope.row.skuId].retrievedAt) }} · {{ dossiers[scope.row.skuId].inventory.scope }}</p>
              <strong>各仓库存</strong><p class="muted">{{ dossiers[scope.row.skuId].inventory.limitation }}</p>
              <el-table :data="dossiers[scope.row.skuId].inventory.records" empty-text="没有匹配库存记录，需核实归属">
                <el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="merchantId" label="商户 ID" /><el-table-column prop="shopId" label="店铺 ID" />
                <el-table-column prop="quantity" label="总库存" /><el-table-column prop="lockedQuantity" label="锁定库存" /><el-table-column prop="availableQuantity" label="可用库存" />
                <el-table-column label="批次核对" min-width="180"><template #default="stock">{{ batchAuditText(stock.row, dossiers[scope.row.skuId].batchAudit) }}</template></el-table-column>
                <el-table-column label="库存类型"><template #default="stock">{{ stock.row.stockStatus === 1 ? '良品' : stock.row.stockStatus === 2 ? '残品' : '待核实' }}</template></el-table-column>
                <el-table-column label="库存更新时间" min-width="170"><template #default="stock">{{ displayTime(stock.row.updateTime) }}</template></el-table-column>
              </el-table>
              <strong>近期销量</strong>
              <p v-if="!dossiers[scope.row.skuId].salesVisible" class="muted">当前账号无销售查询权限或仓库账号不适用，未读取销售数据。</p>
              <template v-else>
                <p>近7个完整日：{{ dossiers[scope.row.skuId].sales7Days?.quantity }} 件；近30个完整日：{{ dossiers[scope.row.skuId].sales30Days?.quantity }} 件。</p>
                <p class="muted">统计窗口 {{ dossiers[scope.row.skuId].sales30Days?.start }} 至 {{ dossiers[scope.row.skuId].sales30Days?.endExclusive }}（不含结束时间）。{{ dossiers[scope.row.skuId].sales30Days?.limitation }}</p>
              </template>
              <strong>采购与入库核对</strong>
              <p v-if="!dossiers[scope.row.skuId].purchaseVisible" class="muted">当前账号无采购查询权限或仓库账号不适用，未读取采购数据。</p>
              <template v-else>
                <p class="muted">{{ dossiers[scope.row.skuId].purchase?.limitation }}</p>
                <el-table :data="dossiers[scope.row.skuId].purchase?.ledger || []" empty-text="没有匹配采购明细">
                  <el-table-column prop="orderNum" label="采购单号" min-width="160" /><el-table-column label="采购状态" min-width="180"><template #default="purchase">{{ purchaseStatus(purchase.row.orderStatus) }}</template></el-table-column><el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="merchantId" label="商户 ID" /><el-table-column prop="shopId" label="店铺 ID" />
                  <el-table-column prop="orderedQuantity" label="采购数量" /><el-table-column prop="receivedQuantity" label="关联任务实入" />
                  <el-table-column label="未入库量"><template #default="purchase">{{ purchase.row.remainingQuantity ?? '待核实' }}</template></el-table-column>
                  <el-table-column prop="checkStatus" label="核对状态" min-width="220" />
                </el-table>
                <details class="source-tasks">
                  <summary>查看采购物流登记</summary>
                  <p class="muted">以下是包含本 SKU 的整单物流登记，不代表本 SKU 的发货数量或预计到货时间；不与未入库量相加，不自动计入有效在途。</p>
                  <el-table :data="dossiers[scope.row.skuId].purchase?.shipments || []" empty-text="没有匹配物流登记，不能据此判断未发货">
                    <el-table-column prop="orderNum" label="采购单号" min-width="160" />
                    <el-table-column prop="warehouseId" label="仓库 ID" />
                    <el-table-column prop="shipCompany" label="物流公司" />
                    <el-table-column prop="shipNum" label="运单号" min-width="150" />
                    <el-table-column prop="shipTime" label="登记发货时间" min-width="180" />
                    <el-table-column prop="receiptTime" label="登记收货时间" min-width="180" />
                    <el-table-column label="登记状态"><template #default="shipment">{{ ({ 0: '未收货', 1: '已收货', 2: '已入库' } as Record<number, string>)[shipment.row.status] || '待核实' }}</template></el-table-column>
                  </el-table>
                </details>
                <details class="source-tasks">
                  <summary>查看待入库任务与未匹配任务的采购（原始资料）</summary>
                  <p class="muted">原始任务可能存在重复或历史关联缺失，与上方台账会重叠；请逐条核对，不将两处数量相加或直接视为有效在途。</p>
                  <el-table :data="dossiers[scope.row.skuId].purchase?.records || []" empty-text="没有匹配的待入库任务">
                    <el-table-column prop="sourceNo" label="来源单号" min-width="150" /><el-table-column prop="sourceItemId" label="来源明细 ID" />
                    <el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="quantity" label="任务数量" /><el-table-column prop="inQuantity" label="已登记入库" />
                    <el-table-column prop="remainingQuantity" label="任务数量差值" /><el-table-column prop="itemStatus" label="原始状态" />
                  </el-table>
                  <el-table :data="dossiers[scope.row.skuId].purchase?.withoutTask || []" empty-text="没有未匹配任务的采购明细">
                    <el-table-column prop="orderNum" label="未匹配任务的采购单号" min-width="170" /><el-table-column prop="itemId" label="采购明细 ID" />
                    <el-table-column label="采购状态"><template #default="purchase">{{ purchaseStatus(purchase.row.orderStatus) }}</template></el-table-column>
                    <el-table-column prop="quantity" label="采购数量" /><el-table-column prop="warehouseId" label="仓库 ID" />
                  </el-table>
                </details>
                <p class="muted">供应商交期与预计到货时间尚无可靠自动来源，需向供应商核实；未入库量不能直接等同于在途量。</p>
              </template>
            </template>
          </div>
        </template></el-table-column>
        <el-table-column prop="risk" label="库存状态" width="140" />
        <el-table-column label="商品" min-width="210"><template #default="scope"><div>{{ scope.row.goodsName }}</div><el-button link type="primary" @click="overviewTable?.toggleRowExpansion(scope.row)">查看采购资料</el-button></template></el-table-column>
        <el-table-column prop="skuCode" label="SKU 编码" min-width="150" />
        <el-table-column prop="skuName" label="规格" min-width="130" />
        <el-table-column label="良品总库存" width="110"><template #default="scope">{{ stockValue(scope.row, scope.row.quantity) }}</template></el-table-column>
        <el-table-column label="良品锁定" width="110"><template #default="scope">{{ stockValue(scope.row, scope.row.lockedQuantity) }}</template></el-table-column>
        <el-table-column label="可用良品" width="110"><template #default="scope">{{ scope.row.risk === '库存数据异常' ? '数据异常' : scope.row.goodRows ? scope.row.availableQuantity : '待核实' }}</template></el-table-column>
        <el-table-column label="预警线" width="100"><template #default="scope">{{ scope.row.lowQty > 0 ? scope.row.lowQty : '未设置' }}</template></el-table-column>
        <el-table-column label="近30日销量参考" min-width="140"><template #default="scope">{{ scope.row.sales30Days ?? '无权限或不适用' }}</template></el-table-column>
        <el-table-column label="采购中单数（不含入库任务阶段）" min-width="220"><template #default="scope">{{ scope.row.openPurchaseOrders ?? '无权限或不适用' }}</template></el-table-column>
        <el-table-column label="缺货范围（仓库 / 商户 / 店铺 ID）" min-width="230"><template #default="scope"><div v-for="item in scope.row.emptyScopes" :key="`${item.warehouseId}-${item.merchantId}-${item.shopId}`">{{ item.warehouseId ?? '未设置' }} / {{ item.merchantId ?? '未设置' }} / {{ item.shopId ?? '未设置' }} <el-button v-if="item.warehouseId" link type="primary" @click="selectOverviewSku(scope.row, item.warehouseId)">核查仓库</el-button></div><span v-if="!scope.row.emptyScopes?.length">—</span></template></el-table-column>
        <el-table-column label="核查提示" min-width="210"><template #default="scope">{{ riskAdvice(scope.row.risk) }}</template></el-table-column>
        <el-table-column label="可选操作" width="140"><template #default="scope"><el-button link type="primary" @click="selectOverviewSku(scope.row)">计算或建草稿</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-if="overview && overview.total > overview.size" class="overview-pagination" layout="total, prev, pager, next" :total="overview.total" :page-size="overview.size" :current-page="overviewPage" @current-change="loadOverview" />
    </el-card>
    <el-card v-if="isHeadquarters" shadow="never" class="section">
      <template #header><div class="overview-header"><strong>我通过助手创建的采购</strong><el-button :loading="recentLoading" @click="loadRecentDrafts">加载最新20条</el-button></div></template>
      <p class="muted">从服务端查询当前账号创建的采购，刷新页面后仍可追踪；需要总部采购查询权限，审核和处理在原采购管理中完成。</p>
      <p v-if="recentError" class="muted">未能获取记录，请核对查询权限、数据库脚本和服务状态后重试。</p>
      <el-table v-if="recentDrafts !== null" :data="recentDrafts" empty-text="当前账号没有助手采购记录">
        <el-table-column prop="orderNum" label="采购单号" /><el-table-column prop="skuId" label="SKU ID" /><el-table-column prop="quantity" label="采购数量" />
        <el-table-column label="当前状态"><template #default="scope">{{ scope.row.status === null ? '单据缺失，待核实' : purchaseStatus(scope.row.status) }}</template></el-table-column>
        <el-table-column label="处理"><template #default="scope"><router-link :to="{ path: '/purchase/purchase_order_detail', query: { id: scope.row.orderId } }">查看及审核</router-link></template></el-table-column>
      </el-table>
    </el-card>
    <el-card v-if="canViewReviews" class="section"><template #header><strong>补货建议审核任务</strong></template>
      <p class="muted">仅总部且具备库存、销售、采购 AI 查询及采购权限的账号可查看。仅显示最近50条审核任务。审核记录不创建或审核采购单；提交前使用最新资料重新计算，审核通过前会再次核对。</p>
      <el-button :loading="reviewsLoading" @click="loadReviews">刷新审核任务</el-button><p v-if="reviewsError">审核任务加载失败，请重试。</p>
      <el-table v-if="reviews" :data="reviews" empty-text="暂无审核任务"><el-table-column prop="skuId" label="SKU ID"/><el-table-column prop="warehouseId" label="仓库 ID"/><el-table-column prop="suggestedQuantity" label="建议数量"/><el-table-column label="状态"><template #default="scope">{{ reviewStatus(scope.row.status) }}</template></el-table-column><el-table-column prop="createdAt" label="提交时间"/><el-table-column label="操作"><template #default="scope"><el-button link type="primary" @click="openReview(scope.row)">查看依据及审核</el-button></template></el-table-column></el-table>
    </el-card>
    <el-collapse v-model="detailPanel" class="section"><el-collapse-item title="可选：单品分析、计算与采购草稿" name="detail">
    <el-card shadow="never">
      <el-form inline @submit.prevent="analyze">
        <el-form-item label="选择商品"><el-autocomplete v-model="skuSearch" :fetch-suggestions="searchSkus" placeholder="搜索商品名称或 SKU 编码" clearable @select="chooseSku" /></el-form-item>
        <el-form-item label="内部 SKU ID"><el-input v-model.trim="skuId" placeholder="输入商品库 SKU ID" clearable @input="skuSearch = ''" /></el-form-item>
        <el-form-item label="仓库 ID"><el-input v-model.trim="warehouseId" placeholder="全部可见仓库" clearable /></el-form-item>
        <el-form-item label="订单周期"><el-select v-model="days" style="width: 135px"><el-option label="近 7 个完整日" :value="7" /><el-option label="近 30 个完整日" :value="30" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" :loading="loading" :disabled="draftLoading" native-type="submit">分析</el-button></el-form-item>
      </el-form>
      <p class="muted">搜索仅包含当前有库存记录的商品，最多显示 30 条；其他商品可直接输入 SKU ID。</p>
      <el-input v-model="question" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="可选：输入想了解的问题，例如库存是否需要关注？" class="question" />
      <el-checkbox v-model="estimate">根据人工确认数据估算补货量</el-checkbox>
      <el-form v-if="estimate" inline class="estimate-form" @submit.prevent="analyze">
        <el-form-item label="日需求（件）"><el-input v-model.trim="dailyDemand" placeholder="人工确认，最多6位小数" /></el-form-item>
        <el-form-item label="目标覆盖（天）"><el-input v-model.trim="targetDays" placeholder="1 至 365" /></el-form-item>
        <el-form-item label="确认在途（件）"><el-input v-model.trim="confirmedIncoming" placeholder="人工核实未入库数量" /></el-form-item>
        <el-form-item v-if="!batchMode" label="确认几天后到货"><el-input v-model.trim="incomingArrivalDays" placeholder="选填，同一批到货" /></el-form-item>
        <el-form-item label="采购交期（天）"><el-input v-model.trim="leadTimeDays" placeholder="选填，人工确认" /></el-form-item>
      </el-form>
      <div v-if="estimate">
        <el-checkbox v-model="batchMode">按多批次到货核算</el-checkbox>
        <div v-if="batchMode">
          <p class="muted">最多50批；每批到同一仓库及主体，数量合计须等于确认在途总量。到货间隔从本次分析日计算，同日到货视为当天起始可用。</p>
          <div v-for="(batch, index) in incomingBatches" :key="index" class="arrival-batch">
            <el-input v-model.trim="batch.quantity" placeholder="本批数量" aria-label="本批数量" />
            <el-input v-model.trim="batch.arrivalDays" placeholder="几天后到货" aria-label="几天后到货" />
            <el-button @click="incomingBatches.splice(index, 1)">移除此批</el-button>
          </div>
          <el-button :disabled="incomingBatches.length >= 50" @click="incomingBatches.push({ quantity: '', arrivalDays: '' })">增加批次</el-button>
        </div>
      </div>
      <p v-if="estimate" class="muted">请指定仓库；日需求需由人工确认，不能直接套用跨仓销量。在途须属于该仓库及对应主体且未计入当前库存；到货间隔适用于全部数量同批到达，超出目标周期将不抵扣。未填到货间隔时，须人工确认全部数量在周期内到达；不同批次请分别核实。估算仅在匹配到一条数量及归属完整的良品库存时显示。</p>
    </el-card>
    <template v-if="result">
      <el-card shadow="never" class="section">
        <template #header><strong>分析结果</strong><span class="muted"> · {{ result.facts.retrievedAt.replace('T', ' ') }}</span></template>
        <p>{{ result.summary }}</p><el-button v-if="canCreateReviews && result.suggestedQuantity && result.suggestedQuantity > 0 && reviewRequest" :loading="reviewSubmitLoading" :disabled="!!submittedReviewId" @click="submitReview">提交建议审核</el-button><p v-if="submittedReviewId" class="muted">已提交审核任务 {{ submittedReviewId }}；可在上方刷新并查看。</p><p v-if="result.suggestedQuantity !== null">建议补货：<strong class="number">{{ result.suggestedQuantity }}</strong> 件（按人工确认条件估算）</p><p v-if="result.facts.assumptions" class="muted">估算条件：日需求 {{ result.facts.assumptions.dailyDemand }} 件，目标 {{ result.facts.assumptions.targetDays }} 天，确认在途 {{ result.facts.assumptions.confirmedIncoming }} 件。</p><div v-if="result.modelExplanation"><p class="muted">AI 回答仅供参考，请核对业务数据与估算条件。</p><p class="model-text">{{ result.modelExplanation }}</p></div>
        <p class="muted">{{ result.modelStatus === 'ok' ? '模型已生成补充说明' : result.modelStatus === 'unavailable' ? '模型不可用，已展示业务数据与规则提示' : '当前展示业务数据与规则提示' }}</p>
        <el-alert v-for="warning in result.warnings" :key="warning" :title="warning" type="warning" :closable="false" show-icon class="notice" />
      </el-card>
      <el-card v-if="result.coverage" shadow="never" class="section">
        <template #header><strong>补货计算依据</strong></template>
        <p>当前库存覆盖 {{ result.coverage.currentCoverageDays }} 天；人工确认日需求 {{ result.coverage.dailyDemand }} 件，目标 {{ result.coverage.targetDays }} 天，对应需求 {{ result.coverage.targetDemand }} 件。</p>
        <p>可用库存 {{ result.coverage.availableQuantity }} 件，人工确认在途 {{ result.coverage.confirmedIncoming }} 件。</p>
        <p>本周期计入到货 {{ result.coverage.effectiveIncoming }} 件；确认到货间隔 {{ result.coverage.incomingArrivalDays ?? '未填写' }} 天，采购交期 {{ result.coverage.leadTimeDays ?? '未填写' }} 天。</p>
        <el-alert v-for="risk in result.coverage.timingRisks" :key="risk" :title="risk" type="warning" :closable="false" class="notice" />
        <div v-if="result.coverage.arrivalProjection">
          <p>首次预计供需缺口：{{ result.coverage.arrivalProjection.firstShortageDay === null ? '目标周期内未出现' : `第 ${result.coverage.arrivalProjection.firstShortageDay} 天` }}；期末供需余额 {{ result.coverage.arrivalProjection.endBalance }} 件。</p>
          <p class="muted">{{ result.coverage.arrivalProjection.limitation }}</p>
          <el-table :data="result.coverage.arrivalProjection.steps"><el-table-column prop="day" label="第几天" /><el-table-column prop="incoming" label="确认到货" /><el-table-column prop="beforeArrival" label="到货前供需余额" /><el-table-column prop="afterArrival" label="到货后供需余额" /></el-table>
        </div>
        <p class="muted">{{ result.coverage.rule }}</p><p class="muted">{{ result.coverage.limitation }}</p>
      </el-card>
      <el-card shadow="never" class="section">
        <template #header><strong>逐仓及主体核查</strong></template>
        <el-table :data="result.inventoryChecks" empty-text="没有库存记录，需先核实归属">
          <el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="merchantId" label="商户 ID" /><el-table-column prop="shopId" label="店铺 ID" />
          <el-table-column prop="status" label="核查状态" /><el-table-column label="问题"><template #default="scope">{{ scope.row.issues.join('；') || '数量及归属检查通过，需求仍须人工确认' }}</template></el-table-column>
        </el-table>
      </el-card>
      <el-card v-if="isHeadquarters" shadow="never" class="section">
        <template #header><strong>创建待审核采购草稿</strong></template>
        <p class="muted">仅总部且有采购创建权限的账号可操作。请核实供应商、数量和协商单价；创建不会审核、发货或改变库存。</p>
        <el-form inline @submit.prevent="submitDraft">
          <el-form-item label="SKU ID"><el-input :model-value="analysisSku" readonly /></el-form-item>
          <el-form-item label="供应商"><el-select v-model="supplierId" :disabled="draftLoading" placeholder="先加载供应商" style="width: 200px"><el-option v-for="supplier in suppliers" :key="supplier.id" :label="supplier.name + ' · ' + supplier.id" :value="supplier.id" /></el-select><el-button :loading="supplierLoading" @click="loadSuppliers">加载</el-button></el-form-item>
          <el-form-item label="采购数量"><el-input v-model.trim="draftQuantity" :disabled="draftLoading" placeholder="正整数" /></el-form-item>
          <el-form-item label="采购单价"><el-input v-model.trim="draftPrice" :disabled="draftLoading" placeholder="最多两位小数" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="draftLoading" native-type="submit">确认并创建草稿</el-button></el-form-item>
        </el-form>
        <p v-if="draftOrderId" class="muted">采购草稿 ID：{{ draftOrderId }}，请到采购订单进行审核。<el-button link :disabled="draftLoading" @click="startAnotherDraft">新建另一单</el-button></p>
        <div v-if="draftOrderId">
          <el-button :loading="progressLoading" @click="loadPurchaseProgress">刷新采购进度</el-button>
          <router-link :to="{ path: '/purchase/purchase_order_detail', query: { id: draftOrderId } }">打开采购单审核及处理</router-link>
          <p v-if="purchaseProgress">采购单 {{ purchaseProgress.orderNum }} · {{ purchaseStatus(purchaseProgress.status) }} · 审核人 {{ purchaseProgress.auditUser || '未记录' }} · 更新时间 {{ purchaseProgress.updateTime || '未记录' }}</p>
          <p v-if="progressError" class="muted">采购进度未获取，请核对采购查询权限、单据是否存在及服务状态后重试。</p>
        </div>
      </el-card>
      <el-card shadow="never" class="section"><template #header><strong>库存记录</strong></template>
        <el-table :data="result.facts.inventory" empty-text="当前范围没有匹配记录"><el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="merchantId" label="商户 ID" /><el-table-column prop="shopId" label="店铺 ID" /><el-table-column prop="quantity" label="总库存" /><el-table-column prop="availableQuantity" label="可用库存" /><el-table-column prop="lockedQuantity" label="锁定库存" /><el-table-column prop="stockStatus" label="库存状态" /></el-table>
      </el-card>
      <el-card shadow="never" class="section"><template #header><strong>订单商品数量</strong></template>
        <p v-if="result.facts.sales"><strong class="number">{{ result.facts.sales.quantity }}</strong> 件 <span class="muted">{{ result.facts.sales.start.replace('T', ' ') }} 至 {{ result.facts.sales.endExclusive.replace('T', ' ') }}（不含结束时间） · {{ result.facts.sales.scope }}</span></p><p v-else class="muted">当前账号未获取销售数据。</p>
      </el-card>
      <el-card v-if="result.facts.purchase" shadow="never" class="section">
        <template #header><strong>采购数量核对台账</strong></template>
        <p class="muted">当前授权主体的所有仓库，含已登记完成的明细；异常关联不显示剩余量。数量不代表已发货或确认到货，不自动计入补货估算。</p>
        <el-table :data="result.facts.purchase.ledger" empty-text="没有匹配采购">
          <el-table-column prop="orderNum" label="采购单号" /><el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="merchantId" label="商户 ID" /><el-table-column prop="shopId" label="店铺 ID" />
          <el-table-column prop="orderedQuantity" label="采购数量" /><el-table-column prop="receivedQuantity" label="关联任务实入" />
          <el-table-column label="未入库量"><template #default="scope">{{ scope.row.remainingQuantity ?? '待核实' }}</template></el-table-column>
          <el-table-column prop="checkStatus" label="核对结果" min-width="220" />
        </el-table>
      </el-card>
      <el-card shadow="never" class="section"><template #header><strong>待核实采购入库任务</strong></template>
        <p class="muted">下列数量只用于核对任务；到货时间与实际运输状态尚待人工确认，不能直接作为有效在途。</p>
        <el-table v-if="result.facts.purchase" :data="result.purchaseChecks" empty-text="没有待核查任务">
          <el-table-column prop="sourceNo" label="来源单号" /><el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="status" label="核查状态" />
          <el-table-column label="问题"><template #default="scope">{{ scope.row.issues.join('；') || '任务数量及状态检查通过，仍需核实到货时间' }}</template></el-table-column>
        </el-table>
        <div v-if="!result.facts.purchase" class="muted">当前账号未获取采购数据。</div>
        <el-table v-else :data="result.facts.purchase.records" empty-text="没有匹配的入库任务"><el-table-column prop="sourceNo" label="来源单号" /><el-table-column prop="warehouseId" label="仓库 ID" /><el-table-column prop="quantity" label="任务数量" /><el-table-column prop="inQuantity" label="已登记入库" /><el-table-column prop="remainingQuantity" label="任务待入库" /><el-table-column prop="itemStatus" label="明细状态" /></el-table>
      </el-card>
      <el-card v-if="result.facts.purchase" shadow="never" class="section"><template #header><strong>未匹配入库任务明细的采购</strong></template>
        <p class="muted">按当前账号查询所有仓库；数量不代表已发货或完整在途。</p>
        <el-table :data="result.facts.purchase.withoutTask" empty-text="没有匹配采购"><el-table-column prop="orderNum" label="采购单号" /><el-table-column prop="orderStatus" label="采购状态" /><el-table-column prop="quantity" label="采购数量" /><el-table-column prop="warehouseId" label="仓库 ID" /></el-table>
      </el-card>
    </template>
    </el-collapse-item></el-collapse>
    <el-dialog v-model="reviewDialog" title="补货建议审核依据" width="75%">
      <template v-if="selectedReview && reviewResult"><p>SKU {{ selectedReview.skuId }} · 仓库 {{ selectedReview.warehouseId }} · 提交人 {{ selectedReview.createdBy }} · {{ displayTime(selectedReview.createdAt) }}</p>
      <p>资料读取时间 {{ displayTime(reviewResult.facts.retrievedAt) }}；这是提交时保存的快照。</p><p>建议 {{ selectedReview.suggestedQuantity }} 件 · {{ reviewStatus(selectedReview.status) }}；审核通过不创建采购单。</p><p>{{ reviewResult.summary }}</p>
      <p v-if="reviewResult.facts.assumptions">人工假设：日需求 {{ reviewResult.facts.assumptions.dailyDemand }}，目标 {{ reviewResult.facts.assumptions.targetDays }} 天，确认在途 {{ reviewResult.facts.assumptions.confirmedIncoming }} 件。</p>
      <p v-if="reviewResult.facts.assumptions">到货间隔 {{ reviewResult.facts.assumptions.incomingArrivalDays ?? '未填' }} 天，采购交期 {{ reviewResult.facts.assumptions.leadTimeDays ?? '未填' }} 天；批次 {{ reviewResult.facts.assumptions.incomingBatches?.map(b => `${b.quantity} 件 / ${b.arrivalDays} 天`).join('，') || '未填' }}。</p>
      <p v-if="reviewResult.coverage">可用库存 {{ reviewResult.coverage.availableQuantity }} 件，本周期计入到货 {{ reviewResult.coverage.effectiveIncoming }} 件，当前覆盖 {{ reviewResult.coverage.currentCoverageDays }} 天。{{ reviewResult.coverage.rule }}</p>
      <p v-if="reviewResult.facts.sales">销售参考 {{ reviewResult.facts.sales.quantity }} 件 · {{ reviewResult.facts.sales.start }} 至 {{ reviewResult.facts.sales.endExclusive }}（不含结束时间）</p>
      <el-alert v-for="warning in reviewResult.warnings" :key="warning" :title="warning" type="warning" :closable="false" class="notice"/>
      <el-table :data="reviewResult.facts.inventory"><el-table-column prop="warehouseId" label="仓库"/><el-table-column prop="merchantId" label="商户"/><el-table-column prop="shopId" label="店铺"/><el-table-column prop="quantity" label="总库存"/><el-table-column prop="lockedQuantity" label="锁定"/><el-table-column prop="availableQuantity" label="可用"/></el-table>
      <p v-if="reviewResult.facts.purchase">{{ reviewResult.facts.purchase.limitation }}</p><el-table v-if="reviewResult.facts.purchase" :data="reviewResult.facts.purchase.ledger" empty-text="无采购台账"><el-table-column prop="orderNum" label="采购单"/><el-table-column prop="orderedQuantity" label="采购数量"/><el-table-column prop="receivedQuantity" label="已入库"/><el-table-column prop="remainingQuantity" label="未入库量"/><el-table-column prop="checkStatus" label="核查结果"/></el-table><p v-if="reviewResult.coverage?.arrivalProjection">{{ reviewResult.coverage.arrivalProjection.limitation }} 首次缺口：{{ reviewResult.coverage.arrivalProjection.firstShortageDay ?? '目标周期内未出现' }}。</p>
      <el-alert v-for="risk in reviewResult.coverage?.timingRisks || []" :key="risk" :title="risk" type="warning" :closable="false" class="notice"/>
      <h4>库存质量核查</h4><el-table :data="reviewResult.inventoryChecks" empty-text="无库存核查记录"><el-table-column prop="warehouseId" label="仓库"/><el-table-column prop="status" label="状态"/><el-table-column label="问题"><template #default="scope">{{ scope.row.issues.join('；') || '未发现数量及归属异常' }}</template></el-table-column></el-table>
      <template v-if="reviewResult.coverage?.arrivalProjection"><h4>多批到货供需计划</h4><el-table :data="reviewResult.coverage.arrivalProjection.steps"><el-table-column prop="day" label="第几天"/><el-table-column prop="incoming" label="确认到货"/><el-table-column prop="beforeArrival" label="到货前供需余额"/><el-table-column prop="afterArrival" label="到货后供需余额"/></el-table></template>
      <h4>采购入库任务核查</h4><el-table :data="reviewResult.purchaseChecks" empty-text="无采购入库任务"><el-table-column prop="sourceNo" label="采购单"/><el-table-column prop="warehouseId" label="仓库"/><el-table-column prop="remainingQuantity" label="任务剩余量"/><el-table-column prop="status" label="状态"/><el-table-column label="问题"><template #default="scope">{{ scope.row.issues.join('；') || '请核实到货时间' }}</template></el-table-column></el-table>
      <h4>未匹配入库任务的采购</h4><el-table v-if="reviewResult.facts.purchase" :data="reviewResult.facts.purchase.withoutTask" empty-text="无未匹配采购"><el-table-column prop="orderNum" label="采购单"/><el-table-column prop="quantity" label="采购数量"/><el-table-column prop="warehouseId" label="仓库"/></el-table>
      <p>审核前请逐项核对人工假设、库存、销量和采购资料。</p>
      <div v-if="selectedReview.status === 'APPROVED' && canCreateReviews"><h4>按审核建议创建采购草稿</h4><p class="muted">数量固定为审核建议；提交时重新核对资料，如资料已变化须重新分析并提交审核。草稿仍需在采购管理中审核；如请求结果不明，请使用相同供应商和单价重试，或在助手采购记录中核对。</p><el-select v-model="reviewSupplier" :disabled="reviewDraftLoading" placeholder="选择供应商" style="width:200px"><el-option v-for="supplier in reviewSuppliers" :key="supplier.id" :label="supplier.name + ' · ' + supplier.id" :value="supplier.id"/></el-select><el-button :loading="reviewSupplierLoading" @click="loadReviewSuppliers">加载供应商</el-button><el-input v-model.trim="reviewPrice" :disabled="reviewDraftLoading" placeholder="协商单价" style="width:140px"/><el-button type="primary" :loading="reviewDraftLoading" :disabled="!!reviewDraftId" @click="submitReviewedDraft">确认创建草稿</el-button><p v-if="reviewDraftId">采购草稿 ID：{{ reviewDraftId }} <router-link :to="{ path: '/purchase/purchase_order_detail', query: { id: reviewDraftId } }">打开采购单审核及处理</router-link></p></div>
      <p v-if="selectedReview.reviewedBy">审核人 {{ selectedReview.reviewedBy }} · {{ displayTime(selectedReview.reviewedAt) }} · {{ selectedReview.reviewNote || '无意见' }}<span v-if="selectedReview.approvedQuantity !== null"> · 审核数量 {{ selectedReview.approvedQuantity }} 件</span></p>
      <template v-if="selectedReview.status === 'PENDING' && canAuditReviews"><p>审核数量 <el-input v-model.trim="reviewQuantity" placeholder="正整数" style="width:140px"/> 件；修改数量时请填写审核意见。</p><el-input v-model="reviewNote" type="textarea" maxlength="500" show-word-limit placeholder="审核意见（选填）"/><p><el-button type="primary" :loading="reviewDecisionLoading" @click="decideReview(true)">核对通过</el-button><el-button :loading="reviewDecisionLoading" @click="decideReview(false)">驳回</el-button></p></template>
      <h4>审核工作流同步</h4>
      <p class="muted">Python 审核流程检查点用于中断恢复；后台会定期自动对账，检查点缺失或不一致时可在此手动同步。同步不改变 ERP 审核状态。</p>
      <el-button :loading="workflowSyncLoading" @click="syncWorkflow">同步审核工作流</el-button>
      <p v-if="workflowSyncError" class="muted">同步失败：请确认 AI 服务可用后重试；后台对账仍会自动重试。</p>
      <p v-else-if="workflowSyncResult">检查点{{ workflowSyncResult.waiting ? '已就绪，等待审核决定' : '已完成审核流程' }}{{ workflowSyncResult.decision ? `（${workflowSyncResult.decision.approved ? '通过' : '驳回'}）` : '' }}。</p>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { createPurchaseDraft, getInventoryDossier, getInventoryAnalysis, getInventoryOverview, getPurchaseSuppliers, getPurchaseProgress, getRecentPurchaseDrafts, searchInventorySku, createInventoryReview, listInventoryReviews, getInventoryReview, decideInventoryReview, createReviewedPurchaseDraft, syncInventoryReviewWorkflow } from '@/api/ai/inventoryAgent'
import type { InventoryAgentResult, InventorySkuChoice, InventoryOverview, InventoryOverviewRow, RecentPurchaseDraft, InventoryDossier, InventoryReviewTask, InventoryReviewSummary, InventoryReviewWorkflowSync } from '@/api/ai/inventoryAgent'
import { useUserStore } from '@/store/modules/user'
function batchAuditText(row: InventoryDossier['inventory']['records'][number], audits: InventoryDossier['batchAudit']) {
  const audit = audits.find(item => String(item.inventoryId) === String(row.id))
  if (!audit || Number(audit.batchCount) === 0) return '缺少批次，待核实'
  if (Number(audit.invalidBatches) > 0 || row.quantity === null || Number(audit.batchQuantity) !== row.quantity) return `批次合计 ${audit.batchQuantity}，数量或归属异常`
  return `批次合计 ${audit.batchQuantity}，与总量一致`
}
const userStore = useUserStore()
const hasReviewBase = computed(() => ['ai:inventory:query','ai:sales:query','ai:purchase:query'].every(p => userStore.permissions.includes('*:*:*') || userStore.permissions.includes(p)))
const hasPermission = (permission: string) => userStore.permissions.includes('*:*:*') || userStore.permissions.includes(permission)
const reviews = ref<InventoryReviewSummary[] | null>(null), reviewsLoading = ref(false), reviewsError = ref(false), reviewSubmitLoading = ref(false), reviewDecisionLoading = ref(false)
const selectedReview = ref<InventoryReviewTask | null>(null), reviewResult = ref<InventoryAgentResult | null>(null), reviewDialog = ref(false), reviewNote = ref('')
const reviewQuantity = ref('')
const reviewRequest = ref<Record<string, unknown> | null>(null), reviewRequestKey = ref(''), submittedReviewId = ref('')
const reviewSupplier = ref(''), reviewPrice = ref(''), reviewDraftId = ref(''), reviewDraftLoading = ref(false), reviewSupplierLoading = ref(false), reviewSuppliers = ref<{id:string;name:string}[]>([])
const workflowSyncLoading = ref(false), workflowSyncError = ref(false), workflowSyncResult = ref<InventoryReviewWorkflowSync | null>(null)
watch([reviewSupplier, reviewPrice], () => { reviewDraftId.value = '' })
function reviewStatus(status: string) { return ({PENDING:'待审核',APPROVED:'已通过',REJECTED:'已驳回'} as Record<string,string>)[status] || status }
async function loadReviews() { reviewsLoading.value = true; reviewsError.value = false; try { reviews.value = (await listInventoryReviews()).data } catch { reviews.value = null; reviewsError.value = true } finally { reviewsLoading.value = false } }
async function openReview(task: InventoryReviewSummary) { const detail = (await getInventoryReview(task.id)).data; selectedReview.value = detail; reviewResult.value = JSON.parse(detail.resultJson); reviewNote.value = ''; reviewQuantity.value = String(detail.suggestedQuantity); reviewSupplier.value = ''; reviewPrice.value = ''; reviewDraftId.value = ''; reviewSuppliers.value = []; workflowSyncResult.value = null; workflowSyncError.value = false; reviewDialog.value = true }
async function syncWorkflow() {
  if (!selectedReview.value || workflowSyncLoading.value) return
  workflowSyncLoading.value = true; workflowSyncError.value = false
  try { const response = await syncInventoryReviewWorkflow(selectedReview.value.id); if (reviewDialog.value) workflowSyncResult.value = response.data }
  catch { if (reviewDialog.value) workflowSyncError.value = true }
  finally { workflowSyncLoading.value = false }
}
async function submitReview() {
  if (!reviewRequest.value || reviewSubmitLoading.value) return
  const request = reviewRequest.value, key = reviewRequestKey.value
  reviewSubmitLoading.value = true
  try { const response = await createInventoryReview(request, key); if (reviewRequestKey.value === key) submittedReviewId.value = response.data.id; ElMessage.success('审核任务已提交'); if (canViewReviews.value) await loadReviews() }
  finally { reviewSubmitLoading.value = false }
}
async function loadReviewSuppliers() {
  if (!selectedReview.value) return
  reviewSupplierLoading.value = true
  const taskId = selectedReview.value.id
  try { const response = await getPurchaseSuppliers(String(selectedReview.value.skuId)); if (selectedReview.value?.id === taskId) { reviewSuppliers.value = response.data; if (!response.data.length) ElMessage.warning('该 SKU 没有关联的可用供应商') } } finally { reviewSupplierLoading.value = false }
}
async function submitReviewedDraft() {
  const task = selectedReview.value
  if (!task || !reviewSupplier.value || !/^\d+(?:\.\d{1,2})?$/.test(reviewPrice.value) || Number(reviewPrice.value) > 999999.99 || Number(reviewPrice.value) * (task.approvedQuantity ?? 0) > 99999999.99) { ElMessage.warning('请选择供应商并填写有效单价，采购总金额不能超过系统上限'); return }
  const supplier = reviewSupplier.value, price = reviewPrice.value
  try { await ElMessageBox.confirm(`SKU ${task.skuId}，数量 ${task.approvedQuantity}，供应商 ${supplier}，单价 ${price}。`, '确认采购草稿', {type:'warning'}) } catch { return }
  if (selectedReview.value?.id !== task.id || reviewSupplier.value !== supplier || reviewPrice.value !== price) { ElMessage.warning('采购内容已变化，请重新确认'); return }
  reviewDraftLoading.value = true
  try { const response = await createReviewedPurchaseDraft(task.id,{version:task.version,supplierId:supplier,unitPrice:price,confirmed:true}); if (selectedReview.value?.id === task.id) reviewDraftId.value = response.data.orderId; ElMessage.success(response.data.replayed ? '已找到此前创建的草稿' : '采购草稿已创建') } finally { reviewDraftLoading.value = false }
}
async function decideReview(approved: boolean) {
  const task = selectedReview.value
  if (!task || reviewDecisionLoading.value) return
  const quantity = approved ? Number(reviewQuantity.value) : null
  if (approved && (!/^[1-9]\d*$/.test(reviewQuantity.value) || quantity! > 2147483647 || (quantity !== task.suggestedQuantity && !reviewNote.value.trim()))) { ElMessage.warning('请填写有效审核数量；修改数量时须填写审核意见'); return }
  try { await ElMessageBox.confirm(approved ? '将使用最新库存、销量与采购资料重新核对；通过不创建采购单。' : '确认驳回该补货建议？', '确认审核', { type: 'warning' }) } catch { return }
  reviewDecisionLoading.value = true
  try { selectedReview.value = (await decideInventoryReview(task.id, task.version, approved, reviewNote.value, quantity)).data; ElMessage.success('审核已记录'); await loadReviews() }
  catch { reviewDialog.value = false; await loadReviews() }
  finally { reviewDecisionLoading.value = false }
}
const recentDrafts = ref<RecentPurchaseDraft[] | null>(null); const recentLoading = ref(false); const recentError = ref(false)
async function loadRecentDrafts() {
  if (recentLoading.value) return
  recentLoading.value = true; recentError.value = false
  try { recentDrafts.value = (await getRecentPurchaseDrafts()).data }
  catch { recentDrafts.value = null; recentError.value = true }
  finally { recentLoading.value = false }
}
const overview = ref<InventoryOverview | null>(null); const overviewLoading = ref(false); const overviewError = ref(false); const overviewPage = ref(1)
// 采购草稿与审核链路后端均为 requireHeadquarters：前端按 subjectType 同步隐藏，
// 避免非总部账号看到可点击却必然 403 的卡片（subjectType 由概览接口返回）。
// 注意：必须定义在 overview 声明之后——watch(canViewReviews,{immediate:true}) 会在
// setup 期间同步求值，过早引用 overview 会触发 TDZ ReferenceError 导致整页白屏。
const isHeadquarters = computed(() => overview.value?.subjectType === 'HEADQUARTERS')
const canViewReviews = computed(() => isHeadquarters.value && hasReviewBase.value && hasPermission('purchase:order:query'))
const canCreateReviews = computed(() => canViewReviews.value && hasPermission('purchase:order:create'))
const canAuditReviews = computed(() => canViewReviews.value && hasPermission('purchase:order:audit'))
watch(canViewReviews, allowed => { if (allowed) void loadReviews(); else reviews.value = null }, { immediate: true })
let dossierEpoch = 0
let overviewSequence = 0
function displayTime(value: string | null) { return value ? value.replace('T', ' ').split('.')[0] : '未记录' }
const overviewTable = ref<TableInstance>()
const dossiers = ref<Record<string, InventoryDossier>>({})
const dossierLoading = ref<Record<string, boolean>>({}); const dossierErrors = ref<Record<string, boolean>>({})
async function loadDossier(id: string) {
  if (dossierLoading.value[id]) return
  const epoch = dossierEpoch
  dossierLoading.value[id] = true; dossierErrors.value[id] = false
  delete dossiers.value[id]
  try { const response = await getInventoryDossier(id); if (epoch === dossierEpoch) dossiers.value[id] = response.data }
  catch { if (epoch === dossierEpoch) dossierErrors.value[id] = true }
  finally { if (epoch === dossierEpoch) dossierLoading.value[id] = false }
}
function expandDossier(row: InventoryOverviewRow, expanded: InventoryOverviewRow[]) {
  if (expanded.some(item => item.skuId === row.skuId) && !dossiers.value[row.skuId]) void loadDossier(row.skuId)
}
function stockValue(row: InventoryOverviewRow, value: number | null) {
  return row.risk === '库存数据异常' ? '数据异常' : row.goodRows ? value ?? '待核实' : '待核实'
}
const detailPanel = ref<string[]>([])
async function loadOverview(page = 1) {
  const sequence = ++overviewSequence
  overviewPage.value = page
  dossierEpoch++; dossiers.value = {}; dossierErrors.value = {}; dossierLoading.value = {}
  overviewLoading.value = true; overviewError.value = false
  try {
    const response = await getInventoryOverview(page)
    if (sequence !== overviewSequence) return
    overview.value = response.data; overviewPage.value = page
  }
  catch { if (sequence === overviewSequence) { overview.value = null; overviewError.value = true } }
  finally { if (sequence === overviewSequence) overviewLoading.value = false }
}
function riskAdvice(risk: string) {
  return ({ '库存数据异常': '核对重复库存和库存数', '批次待核实': '展开核对批次数量和归属', '缺货': '查看销量及已有采购，由采购判断', '部分范围缺货': '核实缺货仓库及主体的需求和在途', '多仓待核实': '逐仓核对库存和补货需求', '低库存': '核对销量、可用库存及采购进度', '待核实库存': '核实库存记录和良品数量', '未设置预警线': '设置 SKU 库存预警线', '正常': '持续关注' } as Record<string, string>)[risk] || '核实库存数据'
}
function selectOverviewSku(row: InventoryOverviewRow, selectedWarehouse = '') {
  analysisSequence++
  loading.value = false
  skuId.value = String(row.skuId); skuSearch.value = [row.goodsName, row.skuName, row.skuCode].filter(Boolean).join(' · ')
  warehouseId.value = selectedWarehouse; estimate.value = false
  dailyDemand.value = ''; targetDays.value = ''; confirmedIncoming.value = ''; incomingArrivalDays.value = ''; leadTimeDays.value = ''; batchMode.value = false; incomingBatches.value = []
  result.value = null; reviewRequest.value = null; reviewRequestKey.value = ''; submittedReviewId.value = ''; detailPanel.value = ['detail']
}
onMounted(() => { void loadOverview() })
const skuId = ref(''); const warehouseId = ref(''); const days = ref(30); const loading = ref(false)
const result = ref<InventoryAgentResult | null>(null)
const analysisSku = ref(''); const supplierId = ref(''); const draftQuantity = ref(''); const draftPrice = ref('')
const suppliers = ref<{ id: string; name: string }[]>([]); const supplierLoading = ref(false)
const purchaseProgress = ref<{ orderNum: string; status: number; auditUser: string | null; updateTime: string | null } | null>(null)
const progressLoading = ref(false); const progressError = ref(false)
const draftLoading = ref(false); const draftOrderId = ref(''); const requestKey = ref('')
watch([analysisSku, supplierId, draftQuantity, draftPrice], () => { requestKey.value = ''; draftOrderId.value = ''; purchaseProgress.value = null; progressError.value = false }, { flush: 'sync' })
const skuSearch = ref('')
type SkuSuggestion = InventorySkuChoice & { value: string }
async function searchSkus(query: string, done: (items: SkuSuggestion[]) => void) {
  if (!query.trim()) { done([]); return }
  try {
    const response = await searchInventorySku(query.trim())
    done(response.data.map(item => ({ ...item, value: [item.goodsName, item.skuName, item.skuCode, `ID ${item.skuId}`].filter(Boolean).join(' · ') })))
  } catch { done([]) }
}
function chooseSku(item: SkuSuggestion) { skuId.value = item.skuId }
const question = ref(''); const estimate = ref(false); const dailyDemand = ref(''); const targetDays = ref(''); const confirmedIncoming = ref(''); const batchMode = ref(false); const incomingBatches = ref<{ quantity: string; arrivalDays: string }[]>([])
const incomingArrivalDays = ref(''); const leadTimeDays = ref('')
let analysisSequence = 0
watch(skuId, () => {
  analysisSequence++; loading.value = false; result.value = null; analysisSku.value = ''
  reviewRequest.value = null; reviewRequestKey.value = ''; submittedReviewId.value = ''
  estimate.value = false; dailyDemand.value = ''; targetDays.value = ''; confirmedIncoming.value = ''
  incomingArrivalDays.value = ''; leadTimeDays.value = ''; batchMode.value = false; incomingBatches.value = []
}, { flush: 'sync' })
async function analyze() {
  if (draftLoading.value) return
  if (!/^[1-9]\d*$/.test(skuId.value) || (warehouseId.value && !/^[1-9]\d*$/.test(warehouseId.value))) { ElMessage.warning('请输入有效的 SKU ID 和仓库 ID'); return }
  if (estimate.value && (!warehouseId.value || !/^(?:\d+)(?:\.\d{1,6})?$/.test(dailyDemand.value) || Number(dailyDemand.value) < 0.000001 || Number(dailyDemand.value) > 1000000 || !/^[1-9]\d*$/.test(targetDays.value) || Number(targetDays.value) > 365 || !/^\d+$/.test(confirmedIncoming.value) || Number(confirmedIncoming.value) > 1000000000)) { ElMessage.warning('请填写有效仓库、日需求、目标天数和确认在途'); return }
  if (estimate.value && [batchMode.value ? '' : incomingArrivalDays.value, leadTimeDays.value].some(value => value !== '' && (!/^\d+$/.test(value) || Number(value) > 3650))) { ElMessage.warning('到货间隔和采购交期须为0至3650天'); return }
  if (estimate.value && batchMode.value && (incomingBatches.value.some(batch => !/^[1-9]\d*$/.test(batch.quantity) || Number(batch.quantity) > 1000000000 || !/^\d+$/.test(batch.arrivalDays) || Number(batch.arrivalDays) > 3650) || incomingBatches.value.reduce((sum, batch) => sum + Number(batch.quantity), 0) !== Number(confirmedIncoming.value))) { ElMessage.warning('请核对每批数量、到货间隔及在途总量'); return }
  loading.value = true; result.value = null; reviewRequest.value = null; reviewRequestKey.value = ''; submittedReviewId.value = ''
  const assumptions = estimate.value ? { dailyDemand: dailyDemand.value, targetDays: targetDays.value, confirmedIncoming: confirmedIncoming.value, incomingBatches: batchMode.value ? incomingBatches.value.map(batch => ({ ...batch })) : undefined, incomingArrivalDays: batchMode.value ? undefined : incomingArrivalDays.value || undefined, leadTimeDays: leadTimeDays.value || undefined } : undefined
  const selectedSku = skuId.value
  const capturedReviewRequest = { skuId: selectedSku, warehouseId: warehouseId.value || undefined, days: days.value, ...assumptions }
  const sequence = ++analysisSequence
  try {
    const response = await getInventoryAnalysis(selectedSku, warehouseId.value || undefined, days.value, assumptions, question.value)
    if (sequence !== analysisSequence) return
    result.value = response.data
    reviewRequest.value = capturedReviewRequest; reviewRequestKey.value = crypto.randomUUID()
    analysisSku.value = selectedSku
    supplierId.value = ''; suppliers.value = []
    draftQuantity.value = result.value.suggestedQuantity && result.value.suggestedQuantity > 0 ? String(result.value.suggestedQuantity) : ''
  } catch { if (sequence === analysisSequence) result.value = null } finally { if (sequence === analysisSequence) loading.value = false }
}
async function loadSuppliers() {
  const selected = analysisSku.value
  supplierLoading.value = true
  try {
    const response = await getPurchaseSuppliers(selected)
    if (analysisSku.value === selected) {
      suppliers.value = response.data
      if (!response.data.length) ElMessage.warning('该 SKU 没有关联的可用供应商，请先维护供应商商品')
    }
  } finally { supplierLoading.value = false }
}
async function submitDraft() {
  if (draftLoading.value) return
  if (!/^[1-9]\d*$/.test(analysisSku.value) || !/^[1-9]\d*$/.test(supplierId.value) || !/^[1-9]\d*$/.test(draftQuantity.value) || Number(draftQuantity.value) > 2147483647 || !/^\d+(?:\.\d{1,2})?$/.test(draftPrice.value) || Number(draftPrice.value) > 999999.99 || Number(draftQuantity.value) * Number(draftPrice.value) > 99999999.99) {
    ElMessage.warning('请填写有效的供应商、采购数量和单价'); return
  }
  const confirmedContent = JSON.stringify([analysisSku.value, supplierId.value, draftQuantity.value, draftPrice.value])
  try { await ElMessageBox.confirm(`SKU ${analysisSku.value}，供应商 ${supplierId.value}，数量 ${draftQuantity.value}，单价 ${draftPrice.value}。创建后仍需审核。`, '确认采购草稿', { type: 'warning', confirmButtonText: '确认创建', cancelButtonText: '取消' }) } catch { return }
  if (draftLoading.value || confirmedContent !== JSON.stringify([analysisSku.value, supplierId.value, draftQuantity.value, draftPrice.value])) {
    ElMessage.warning('采购内容已变化，请重新确认'); return
  }
  const fingerprint = JSON.stringify([userStore.name, analysisSku.value, supplierId.value, draftQuantity.value, draftPrice.value])
  if (!requestKey.value) {
    try {
      const previous = JSON.parse(sessionStorage.getItem('aiPurchaseDraftPending') || 'null')
      requestKey.value = previous?.fingerprint === fingerprint && typeof previous?.key === 'string' ? previous.key : crypto.randomUUID()
      sessionStorage.setItem('aiPurchaseDraftPending', JSON.stringify({ fingerprint, key: requestKey.value }))
    } catch { requestKey.value = crypto.randomUUID() }
  }
  draftLoading.value = true
  try {
    const response = await createPurchaseDraft({ requestKey: requestKey.value, skuId: analysisSku.value, supplierId: supplierId.value, quantity: draftQuantity.value, unitPrice: draftPrice.value, confirmed: true })
    draftOrderId.value = String(response.data.orderId)
    ElMessage.success(response.data.replayed ? '已找到此前创建的采购草稿' : '采购草稿已创建，等待审核')
  } finally { draftLoading.value = false }
}
function purchaseStatus(status: number) {
  return ({ 0: '待审核', 1: '已审核', 101: '供应商已确认', 102: '供应商已发货', 2: '已收货', 3: '已生成入库任务，实入数量请核对入库管理', 99: '已取消' } as Record<number, string>)[status] || `待核实状态（${status}）`
}
async function loadPurchaseProgress() {
  const id = draftOrderId.value
  if (!id || progressLoading.value) return
  progressLoading.value = true; progressError.value = false
  try {
    const response = await getPurchaseProgress(id)
    if (draftOrderId.value !== id) return
    purchaseProgress.value = response.data
    progressError.value = !response.data
  } catch { if (draftOrderId.value === id) { purchaseProgress.value = null; progressError.value = true } }
  finally { progressLoading.value = false }
}
function startAnotherDraft() {
  purchaseProgress.value = null; progressError.value = false
  sessionStorage.removeItem('aiPurchaseDraftPending')
  requestKey.value = ''; draftOrderId.value = ''
}
</script>
<style scoped>
.assistant-page { max-width: 1320px; margin: 0 auto; padding: 24px; color: #263344; }
.heading h1 { margin: 0; font-size: 24px; }.heading p { color: #66768a; margin: 8px 0 20px; }
.dossier { padding: 20px 24px; width: min(1100px, calc(100vw - 340px)); max-width: 100%; box-sizing: border-box; overflow-wrap: anywhere; } .source-tasks { margin: 16px 0; } .source-tasks summary { cursor: pointer; color: #337ecc; } .dossier > strong { display: block; margin-top: 16px; }
.arrival-batch { display: flex; gap: 12px; margin: 10px 0; max-width: 600px; }
.question { margin: 0 0 12px; }.section { margin-top: 16px; }.estimate-form { margin-top: 12px; }.muted { color: #738096; font-size: 13px; }.notice { margin-top: 8px; }.number { font-size: 26px; color: #2470d3; }.model-text { white-space: pre-wrap; line-height: 1.7; }
.overview-header { display: flex; justify-content: space-between; align-items: center; }.overview-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
