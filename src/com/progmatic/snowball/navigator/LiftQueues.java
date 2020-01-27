package com.progmatic.snowball.navigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

enum CheckDsp {
    Ok,
    EmptyData,
    Sigma3Rule,
    SigmaTooBig,
}

public class LiftQueues {
    final static long CURRENT_QUEUE_LIVE_TIME = 1800; // время жизни данных о текущей очереди (секунды)
    final static long TRUST_TIME = 1800; // допустимое время ожидания при отсутствии других данных по очередям
    final static long MAX_WAIT_TIME_SIGMA = 180; // максимальная сигма, при которой данные сичтаем корректными
    final static long MIN_WAIT_TIME_SIGMA = 30; // минимальная сигма (секунды) - если сигма меньше, то берём это значение
    
    private final Object lock = new Object();
    
    public class QueueMatStat {
        public double avg_wait;
        public double dsp_wait;
        public double sigma;
    }
    
    public class LiftInfo {
        long transitionId;
        boolean globalStatInitialized = false;
        double avgWait; // глобальное среднее время ожидания
        double dspWait; // глобальное дисперсия времени ожидания
        double sigmaWait; // глобальное среднеквардатичное отклонение времени ожидания
        
        List<LiftQueueStat.LiftQueue> currentLiftQueues = new ArrayList<>(); // остортированы по времени
        TreeMap<Short, LiftQueueStat.LiftQueueMathStat> liftQueueMathStats = new TreeMap<>();
        
        LiftInfo(long transition_id) {
            this.transitionId = transition_id;
        }
        
        LiftInfo(LiftQueueStat.GlobalLiftQueueMatStat queueMatStat) {
            this.transitionId = queueMatStat.transition_id;
            this.globalStatInitialized = true;
            this.dspWait = queueMatStat.dsp_wait;
            this.avgWait = queueMatStat.avg_wait;
            this.sigmaWait = this.dspWait > 0 ? Math.sqrt(dspWait) : 0;
        }

        QueueMatStat getCurrentStat(LiftQueueStat.LiftQueue liftQueue) {
            QueueMatStat matStat = new QueueMatStat();
            int size = currentLiftQueues.size();
            if (size > 0) {
                LiftQueueStat.LiftQueue lastLiftQueue = null;
                for (LiftQueueStat.LiftQueue lq : currentLiftQueues) {
                    matStat.avg_wait += lq.waitTime;
                    if (lastLiftQueue != null) {
                        double diff = lastLiftQueue.waitTime - lq.waitTime;
                        matStat.dsp_wait += diff * diff;
                    }
                    lastLiftQueue = lq;
                }

                // добавляем к дисперсии новое значение
                if (lastLiftQueue != null) {
                    double diff = lastLiftQueue.waitTime - liftQueue.waitTime;
                    matStat.dsp_wait += diff * diff;
                }

                matStat.dsp_wait /= size;
                matStat.avg_wait /= size;
                matStat.sigma = Math.sqrt(matStat.dsp_wait);
            }
            return matStat;
        }

        QueueMatStat getCurrentStat() {
            int size = currentLiftQueues.size();
            if (size < 2)
                return null;
            
            QueueMatStat matStat = new QueueMatStat();
            LiftQueueStat.LiftQueue lastLiftQueue = null;
            for (LiftQueueStat.LiftQueue lq : currentLiftQueues) {
                matStat.avg_wait += lq.waitTime;
                if (lastLiftQueue != null) {
                    double diff = lastLiftQueue.waitTime - lq.waitTime;
                    matStat.dsp_wait += diff * diff;
                }
                lastLiftQueue = lq;
            }

            matStat.dsp_wait /= size - 1;
            matStat.avg_wait /= size;
            matStat.sigma = Math.sqrt(matStat.dsp_wait);
            
            return matStat;
        }

        CheckDsp checkCurrentDsp(LiftQueueStat.LiftQueue liftQueue) {
            if (currentLiftQueues.isEmpty())
                return CheckDsp.EmptyData;
            QueueMatStat qms = getCurrentStat(liftQueue);
            
            double locSigma = qms.sigma < MIN_WAIT_TIME_SIGMA ? MIN_WAIT_TIME_SIGMA : qms.sigma;
            
            if (locSigma > MAX_WAIT_TIME_SIGMA)
                return CheckDsp.SigmaTooBig;
            if (Math.abs(qms.avg_wait - liftQueue.waitTime) > 3 * locSigma)
                return CheckDsp.Sigma3Rule;
            
            return CheckDsp.Ok;
        }
        
        boolean checkHistoryDsp(LiftQueueStat.LiftQueue liftQueue) {
            short halfHourNum = LiftQueueStat.halfHourNum(liftQueue.time);
            LiftQueueStat.LiftQueueMathStat liftQueueMathStat = liftQueueMathStats.get(halfHourNum);
            if (liftQueueMathStat == null)
                return false;
            return liftQueueMathStat.check3sigma(liftQueue.waitTime);
        }
        
        CheckDsp checkGlobalDsp(LiftQueueStat.LiftQueue liftQueue) {
            if (!globalStatInitialized)
                return CheckDsp.EmptyData;
            
            double locSigma = sigmaWait < MIN_WAIT_TIME_SIGMA ? MIN_WAIT_TIME_SIGMA : sigmaWait;
            
            if (locSigma > MAX_WAIT_TIME_SIGMA)
                return CheckDsp.SigmaTooBig;
            if (Math.abs(avgWait - liftQueue.waitTime) > 3 * locSigma)
                return CheckDsp.Sigma3Rule;
            return CheckDsp.Ok;
        }
        
        boolean checkLiftQueue(LiftQueueStat.LiftQueue liftQueue) {
            // проверка по оперативным данным
            CheckDsp ccDsp = checkCurrentDsp(liftQueue);
            if (ccDsp == CheckDsp.Ok)
                return true;
            
            // проверка по глобальным данным
            CheckDsp cgDsp = checkGlobalDsp(liftQueue);
            if (cgDsp == CheckDsp.Ok)
                return true;
            
            if (ccDsp != CheckDsp.Sigma3Rule && cgDsp != CheckDsp.Sigma3Rule && liftQueue.waitTime <= TRUST_TIME)
                // при отсутствии достоверных данных имеем вполне допустимое время ожидания
                return true;
            
            return false;
        }
        
        void removeObsoleteQueues(long obsoleteTime) {
            currentLiftQueues.removeIf(lqp-> lqp.time < obsoleteTime);
        }
        
        void addCurrentQueue(LiftQueueStat.LiftQueue liftQueue, long now) {
            long obsoleteTime = now - CURRENT_QUEUE_LIVE_TIME;
            removeObsoleteQueues(obsoleteTime);
            if (liftQueue.time >= obsoleteTime) {
                if (checkLiftQueue(liftQueue)) {
                    currentLiftQueues.add(liftQueue);
                }
            }
        }
    }
    
    // свойства
    Map<Long, LiftInfo> liftInfos = Collections.synchronizedMap(new TreeMap<>());
    
    public LiftQueues(ArrayList<LiftQueueStat.GlobalLiftQueueMatStat> globalLiftQueueDsps) {
        if (globalLiftQueueDsps != null) {
            // глобальная дисперсия используется для оценки правдоподобия входящих данных (при их малом количестве)
            for (LiftQueueStat.GlobalLiftQueueMatStat globalQMS : globalLiftQueueDsps) {
                LiftInfo li = new LiftInfo(globalQMS);
                liftInfos.put(li.transitionId, li);
            }
        }
    }
    
    public static int getLocalTimeOffset(String timeZone) {
        // смещение локального времени в секундах
        int ofs = 0;
        TimeZone tz = TimeZone.getTimeZone(timeZone);
        if (tz != null) {
            long now = System.currentTimeMillis();
            ofs = tz.getOffset(now) / 1000;
        }
        
        return ofs;
    }

    //public static long getLocalNow(String timeZone) {
    //    // локальное текущее время
    //    return System.currentTimeMillis() / 1000 + getLocalTimeOffset(timeZone);
    //}
    
    public static long getNow() {
        // локальное текущее время
        return System.currentTimeMillis() / 1000;
    }
    
    // Функции для Игоря:
    // добавить текущую очередь
    public void addQueue(LiftQueueStat.LiftQueue liftQueue/*, String timeZone*/) {
        synchronized(lock) {
            LiftQueues.LiftInfo liftInfo = liftInfos.get(liftQueue.transitionId);
            if (liftInfo == null) {
                liftInfo = new LiftQueues.LiftInfo(liftQueue.transitionId);
                liftInfos.put(liftInfo.transitionId, liftInfo);
            }
            liftInfo.addCurrentQueue(liftQueue, getNow());
        }
    }
    
    // получить список очередей по списку номеров коннекшенов
    public ArrayList<LiftQueueStat.LiftQueue> getCurrentQueues(ArrayList<Long> connectionIds/*, String timeZone*/) {
        long now = getNow();
        ArrayList<LiftQueueStat.LiftQueue> result = new ArrayList<>();
        synchronized(lock) {
            for (Long conId : connectionIds) {
                // нормальное направление
                LiftInfo liNorm = liftInfos.get(conId);
                if (liNorm != null) {
                    LiftQueues.QueueMatStat normQMS = liNorm.getCurrentStat();
                    if (normQMS != null) {
                        result.add(new LiftQueueStat.LiftQueue(conId, now, (long)normQMS.avg_wait));
                    }
                }

                // обратное направление
                LiftInfo liBack = liftInfos.get(-conId);
                if (liBack != null) {
                    LiftQueues.QueueMatStat backQMS = liBack.getCurrentStat();
                    if (backQMS != null) {
                        result.add(new LiftQueueStat.LiftQueue(-conId, now, (long)backQMS.avg_wait));
                    }
                }
            }
        }
        
        return  result;
    }
}