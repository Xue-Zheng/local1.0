package nz.etu.voting.service;

import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;

import java.util.List;
import java.util.Map;

public interface EventMemberImportService {

    //    根据事件类型导入会员
    ImportResult importMembersForEvent(Event event);

    //    Manufacturing Food 会议 - 从数据库筛选manufacturing food sub-industry会员
    ImportResult importManufacturingFoodMembers(Event event);

    //    BMM Voting 会议 - 导入所有活跃会员
    ImportResult importAllActiveMembers(Event event);

    //    其他会议 - 从Informer链接获取会员
    ImportResult importFromInformerDataset(Event event, String datasetId);

    //    批量创建EventMember记录
    List<EventMember> createEventMembers(Event event, List<Map<String, Object>> memberData);

    //    获取Manufacturing Food相关的会员筛选条件
    Map<String, Object> getManufacturingFoodCriteria();

    class ImportResult {
        private final int totalProcessed;
        private final int successCount;
        private final int errorCount;
        private final List<String> errors;
        private final String importSource;

        public ImportResult(int totalProcessed, int successCount, int errorCount,
                            List<String> errors, String importSource) {
            this.totalProcessed = totalProcessed;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.errors = errors;
            this.importSource = importSource;
        }

        //        Getters
        public int getTotalProcessed() { return totalProcessed; }
        public int getSuccessCount() { return successCount; }
        public int getErrorCount() { return errorCount; }
        public List<String> getErrors() { return errors; }
        public String getImportSource() { return importSource; }
    }
}