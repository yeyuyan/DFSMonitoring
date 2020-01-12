'''
Created on Oct 14, 2014

@author: Jay <smile665@gmail.com>
'''

import ast
import json

def my_run():
    try:
        s = '{"host":"192.168.11.22", "port":3306, "user":"abc",\
              "passwd":"123", "db":"mydb", "connect_timeout":10}'
        d = ast.literal_eval(s)
        print (type(d))
        print (d)
        d1 = eval(s)
        print (type(d1))
        print (d1)
        d2 = json.loads(s)
        print (type(d2))
        print (d2)
        print ('right')
    except Exception:
        print ('wrong %s' % e)


if __name__ == '__main__':
    my_run()
