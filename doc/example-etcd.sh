#!/bin/bash

etcdctl mkdir /PLANC/virtualhosts/test.com/rules/$(echo '/' | base64)
etcdctl set /PLANC/virtualhosts/test.com/rules/$(echo '/' | base64)/order 0
etcdctl set /PLANC/virtualhosts/test.com/rules/$(echo '/' | base64)/target 0
etcdctl set /PLANC/virtualhosts/test.com/rules/$(echo '/' | base64)/type PATH
etcdctl set /PLANC/virtualhosts/test.com/allow 127.0.0.0/8,172.16.0.1
etcdctl mkdir /PLANC/pools/0/targets
etcdctl set /PLANC/pools/0/loadbalance ROUNDROBIN
etcdctl set /PLANC/pools/0/targets/0 http://127.0.0.1:8080
