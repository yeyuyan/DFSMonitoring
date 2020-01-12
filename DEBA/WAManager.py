import threading
import time

import logging
logging.basicConfig(level = logging.DEBUG,
        format = '%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s) %(threadName)s %(message)s',
        datefmt = '%d %b %Y %H:%M:%S',
        filename = 'log_rpi.log',filemode = 'w')

#fake data to test
import random as rd
chn_l = [52,56,60]
frq_l = [5250,5270,5280,5290]
strength_l = [-50,-30,-10,0,20]


class listenThread(threading.Thread):
#threads to listen the dfs event
    def __init__(self,name,commu_thread):
        #to initialize the listening thread
        threading.Thread.__init__(self)
        self.name = name
        self.commu_thread = commu_thread
        self.dfs_lst = []#list of dfs events
        self.nb_dfs = 0#nb of dfs event listened by the thread
        self.is_landed = False
        #self.__running = threading.Event()#internal flag to stop the thread
        #self.__running.set()
        
    
    def run(self):
        logging.debug('Started.')
        while True:
        # start listening dfs event
            if self.is_landed:
                break
            elif self.detect_dfs():
                logging.debug('dfs event detected.')
                self.commu_thread.send_msg('DFSevent:'+str(self.nb_dfs))
                self.dfs_lst.append(listenThread.get_dfs_event(self.nb_dfs))
                self.nb_dfs += 1
                

    def get_dfs_event(nb_dfs):
        #return a dfs event including the information: time,channel,fequency and strength
        dfs_ev = DFS_event(nb_dfs)
        #fake dfs event infomation
        global chn_l,frq_l,strength_l
        chn = chn_l[rd.choice(range(0,len(chn_l)))]
        frq = frq_l[rd.choice(range(0,len(frq_l)))]
        strength = strength_l[rd.choice(range(0,len(strength_l)))]
        dfs_ev.get_info(chn,frq,strength)
        return dfs_ev

    def stop(self):
        self.is_landed = True 
        logging.debug('stoped.')

    def detect_dfs(self):
        # to detect the dfs event
        # un fake dfs event happens every 5 seconds
        time.sleep(5)
        return True


class DFS_event():
    def __init__(self,nb_dfs):
        self.nb_dfs = nb_dfs
        self.chn = None
        self.frq = None
        self.strength = None 
        self.time = None
    
    def get_info(self,chn,frq,strength):
        # to get information abou the channel, frequency, strength of the event
        self.chn = chn
        self.frq = frq
        self.strength = strength



