import json 
import threading

import logging
logging.basicConfig(level = logging.DEBUG,
        format = '%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s) %(threadName)s %(message)s',
        datefmt = '%d %b %Y %H:%M:%S',
        filename = 'log_rpi.log',filemode = 'w')

LOG_NAME = 'Log_data.json'

def add_flight_data(dict_data,data):
    #to store data in a dict dict_data, will be called by app controller when receives flight data
    type_data = data.pop("type")
    dict_data.update(data)
    if type_data == 0:
        logging.info("Add flight data")
    elif type_data == 1:
        logging.info("Add take-off event")
    elif type_data == 2:
        logging.info("Add landing event")
    
    

def add_dfs_event(dict_dfs,dfs_event):
    #to store dfs event in a dict dict_data,will be called by the thread which records the dfs events
    dfs_ev = {'DtTmDFS' : dfs_event.time,'Chn' : dfs_event.chn,'Frq' : dfs_event.frq,'SiSt' : dfs_event.strength}
    try:
        dict_dfs.update({str(dfs_event.nb_dfs) : dfs_ev})
        return True
    except:
        logging.error('Dictionary can not update.')
        return False


def write_log_file(dict_data):
    #after landing we write the data log file in json format
    with open (LOG_NAME,'w')as f:
        try:
            json.dump(dict_data,f,indent = 4)
            f.close()
            logging.info('log file was wrote: ' + LOG_NAME )
            return True
        except:
            f.close()
            logging.error('dumping dict into a file failed.')
            return False



class addDFSThread(threading.Thread):
    #thread who records dfs events record by a listen_thread
    def __init__(self,name,thread):
        threading.Thread.__init__(self)
        self.name = name
        self.dict_dfs = dict()
        self.listen_thread = thread
        self.is_landed = False
        self.dfs_event = None
    def run(self):
        logging.debug('started.')
        while not self.is_landed:
            if len(self.listen_thread.dfs_lst) != 0:
                self.dfs_event = self.listen_thread.dfs_lst.pop(0)
                if add_dfs_event(self.dict_dfs,self.dfs_event):
                    logging.info("Add dfs event No." +str(self.dfs_event.nb_dfs)) 

    def stop(self):
        self.is_landed = True
        logging.debug('stoped')

    def get_dict_dfs(self):
        return self.dict_dfs

class addDFSTime(threading.Thread):
    def __init__(self,name,data,thread):
        self.data = data
        self.name = name
        self.thread = thread

    def run(self):
        logging.debug("started")

