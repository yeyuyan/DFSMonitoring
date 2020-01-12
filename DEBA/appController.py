import logfileManager as lf
import WAManager as wa
import threading
import json
import sys

import logging
logging.basicConfig(level = logging.DEBUG,
        format = '%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s) %(threadName)s %(message)s',
        datefmt = '%d %b %Y %H:%M:%S',
        filename = 'log_rpi.log',filemode = 'w')

#def send_log_succeeded():
    # first to run when the app runs to check if 
    # the log file was sent successfully for the last time
    # if not, send it before the beginning of this process 

def add_flight_data (dict_data,data):
    #write the log file and send the result back to the client
    lf.add_flight_data(dict_data,data)
    return True

def add_time_to_dfs(time_lst,dict_dfs):
    for dict_time in time_lst:
        if dict_time['nb_dfs'] in dict_dfs.keys():
            #try:
            dict_dfs[dict_time['nb_dfs']].update({'DtTmDFS':dict_time['DtTmDFS']})
            #except:
            #    e = sys.exc_info()[0]
            #    logging.error('Add time for DFS event failed:'+str(e))
    return dict_dfs


def start(server):
    logging.info('Reinitiation for one flight.')
    dict_data = dict()
    dict_data.update({'DFSevent': None})
    f_data_received = False
    time_lst = []
    data_collect = 0
    while(data_collect < 3):
        if server.commu_thread != None and len(server.commu_thread.data_recv) != 0:
            data=server.commu_thread.data_recv.pop(0)
            #here data's form is changed from byte[] to string(decode)and then to dict
            data = json.loads(data.decode("UTF-8"))
            type_data = data["type"]
            
            if type_data == 0:
                data_collect += 1
                f_data_received = True
                if add_flight_data(dict_data,data):
                    server.commu_thread.send_msg("Add flight data successfully")
                continue

            if type_data == 1 and not server.f_is_flying:
                data_collect += 1
                listen_thread = wa.listenThread('listen_thread',server.commu_thread)
                listen_thread.start()
                logging.info('Start monitoring.')
                server.f_is_flying = True
                add_dfs_thread = lf.addDFSThread('add_dfs_thread',listen_thread)
                add_dfs_thread.start()
                if add_flight_data(dict_data,data):
                    server.commu_thread.send_msg("Add take-off event successfully.")
                continue

            if type_data == 2 and server.f_is_flying:   
                data_collect += 1
                listen_thread.stop()
                add_dfs_thread.stop()
                logging.info('End monitoring')
                server.f_is_flying = False
                if add_flight_data(dict_data,data):
                    server.commu_thread.send_msg("Add landing event successfully")
                if not f_data_received:
                    server.commu_thread.send_msg("lack of flight data")
                continue

            if type_data == 3:
                print(data)
                del data["type"]
                time_lst.append(data)
                
                
    
    #when flight data, take-off event and landing event are all received, write log file  
    add_dfs_thread.dict_dfs = add_time_to_dfs(time_lst,add_dfs_thread.dict_dfs)
    dict_data.update({'DFSevent':add_dfs_thread.get_dict_dfs()})
    listen_thread = None
    add_dfs_thread = None       
    logging.info('reception of all data.')
    if lf.write_log_file(dict_data):
        server.commu_thread.send_file(lf.LOG_NAME)
        logging.info("log file finished and sent to client")



def main():
    logging.info('DEBA start working.')
    import bluetoothController as bt
    bt.open_bt()
    server_sock = bt.build_server_socket()
    server = bt.serverThread('server',server_sock)
    server.start()
    while(True):
        start(server)
    logging.info('DEBA end working.')

main()
#how to stop the procedure?
#server.stop()
#bt.close_bt()

    


    
    
    
    
    
    






