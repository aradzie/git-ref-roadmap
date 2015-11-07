#!/bin/bash

source "$(git --exec-path)/git-sh-setup"

function _roadmap() {
    java -jar ./lib/git-ref-roadmap.jar
}

_roadmap
